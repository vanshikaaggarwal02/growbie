package com.launchmate.growth;

import com.launchmate.ai.AiJson;
import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.ai.Quotes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the model's growth plan. Each recommendation must cite evidence ids that exist and quote
 * a line of the evidence; its module is taken from the first id it cites, and the model's own
 * priority is not used (priority is computed from impact and effort). First attempt is strict, the
 * retry drops what cannot be verified, exactly as for the audit and the channel analysis.
 */
final class PlanOutputParser {

    record Rec(String title, String module, String impact, String effort, List<String> sourceIds, String evidence,
               String rationale, String suggestedAction, String howToMeasure, String basis) {}

    record Output(String summary, List<Rec> recommendations, List<String> limitations, int dropped) {}

    private static final Set<String> MODULES = Set.of("WEBSITE", "CHANNEL", "DOMAIN", "BRAND", "LAUNCH", "GEO");
    private static final Set<String> LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> BASES = Set.of("OBSERVED", "USER_PROVIDED", "INFERENCE");
    private static final Map<String, String> PREFIX_MODULE = Map.of(
            "AUDIT", "WEBSITE", "CHANNEL", "CHANNEL", "DOMAIN", "DOMAIN", "BRAND", "BRAND", "LAUNCH", "LAUNCH",
            "GEO", "GEO", "DOMAINREVIEW", "DOMAIN");
    static final int MIN_RECOMMENDATIONS = 3;
    static final int MAX_RECOMMENDATIONS = 10;

    private PlanOutputParser() {}

    static Output parse(ObjectMapper json, String text, Set<String> ids, String evidenceText, boolean strict) {
        JsonNode root = AiJson.parseObject(json, text);
        String summary = AiJson.str(root, "summary", 1, 600);
        String haystack = Quotes.normalise(evidenceText);

        List<Rec> kept = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        int dropped = 0;

        for (JsonNode r : AiJson.array(root, "recommendations", MIN_RECOMMENDATIONS, MAX_RECOMMENDATIONS)) {
            if (!r.isObject()) {
                throw new InvalidAiOutputException("Every item in 'recommendations' must be an object.");
            }
            String title = AiJson.str(r, "title", 1, 120);
            String claimedModule = AiJson.oneOf(r, "module", MODULES);
            String impact = AiJson.oneOf(r, "impact", LEVELS);
            String effort = AiJson.oneOf(r, "effort", LEVELS);
            List<String> sources = new ArrayList<>();
            for (String raw : AiJson.strings(r, "sourceIds", 1, 4, 40)) {
                sources.add(raw.replace("[", "").replace("]", "").trim());
            }
            String evidence = AiJson.str(r, "evidence", 4, 300);
            String rationale = AiJson.str(r, "rationale", 1, 400);
            String action = AiJson.str(r, "suggestedAction", 1, 500);
            String measure = AiJson.str(r, "howToMeasure", 1, 300);
            String basis = AiJson.oneOf(r, "basis", BASES);

            String problem = null;
            for (String id : sources) {
                if (!ids.contains(id)) {
                    problem = "it cites '" + id + "', which is not an id in the EVIDENCE";
                    break;
                }
            }
            if (problem == null && !Quotes.appearsIn(haystack, evidence)) {
                problem = "its evidence is not copied exactly from one line of the EVIDENCE";
            }
            if (problem != null) {
                problems.add("'" + title + "': " + problem);
                dropped++;
                continue;
            }
            if (titles.add(Quotes.normalise(title))) {
                kept.add(new Rec(title, moduleFor(sources.get(0), claimedModule), impact, effort,
                        List.copyOf(sources), evidence, rationale, action, measure, basis));
            }
        }

        if (strict && !problems.isEmpty()) {
            throw new InvalidAiOutputException("These recommendations could not be verified against the evidence: "
                    + String.join("; ", problems.subList(0, Math.min(3, problems.size()))) + ".");
        }
        if (kept.size() < MIN_RECOMMENDATIONS) {
            throw new InvalidAiOutputException("Fewer than " + MIN_RECOMMENDATIONS
                    + " verifiable recommendations remained. Each must cite real evidence ids and quote a line.");
        }
        return new Output(summary, List.copyOf(kept), AiJson.strings(root, "limitations", 0, 5, 300), dropped);
    }

    /** The module follows the evidence the recommendation rests on; the model's choice is a fallback. */
    static String moduleFor(String firstId, String claimed) {
        int dash = firstId.indexOf('-');
        String prefix = dash < 0 ? firstId : firstId.substring(0, dash);
        return PREFIX_MODULE.getOrDefault(prefix, claimed);
    }
}
