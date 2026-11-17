package com.launchmate.content;

import com.launchmate.ai.AiJson;
import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.ai.Quotes;
import com.launchmate.content.ContentSpecs.PartSpec;
import com.launchmate.content.ContentSpecs.Spec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates generated content against its type's spec and against the two guards. A variant that
 * invents a number or makes a claim the owner never made is not shown. As elsewhere, the first
 * attempt is strict (the reply fails and the model is told why) and the retry drops the offending
 * variants and counts them.
 */
final class ContentOutputParser {

    record Part(String name, String text) {}

    record Variant(String label, List<Part> parts, String reason) {

        String joinedText() {
            StringBuilder sb = new StringBuilder(label).append(' ').append(reason);
            for (Part p : parts) {
                sb.append(' ').append(p.text());
            }
            return sb.toString();
        }
    }

    record Output(List<Variant> variants, List<String> notes, int dropped) {}

    private ContentOutputParser() {}

    /**
     * @param guardText everything the owner supplied or LaunchMate measured, for the number and claim guards
     * @param existingTitles titles the creator already uses; a new YouTube title may not repeat one
     */
    static Output parse(ObjectMapper json, String text, Spec spec, String guardText, Set<String> existingTitles,
                        boolean strict) {
        JsonNode root = AiJson.parseObject(json, text);
        Set<String> known = new HashSet<>();
        for (String t : existingTitles) {
            known.add(Quotes.normalise(t));
        }

        List<Variant> kept = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int dropped = 0;

        for (JsonNode v : AiJson.array(root, "variants", spec.minVariants(), spec.maxVariants())) {
            if (!v.isObject()) {
                throw new InvalidAiOutputException("Every item in 'variants' must be an object.");
            }
            Variant variant = new Variant(AiJson.str(v, "label", 1, 60), parts(v, spec), AiJson.str(v, "reason", 1, 300));

            String problem = null;
            List<String> numbers = ContentGuards.unsupportedNumbers(variant.joinedText(), guardText);
            if (!numbers.isEmpty()) {
                problem = "it uses " + String.join(", ", numbers) + ", which the owner never provided; remove or "
                        + "replace those numbers";
            }
            List<String> claims = ContentGuards.unsupportedClaims(variant.joinedText(), guardText);
            if (problem == null && !claims.isEmpty()) {
                problem = "it makes a claim (" + String.join(", ", claims) + ") that the owner never made";
            }
            if (problem == null && "YT_TITLE".equals(spec.type()) && known.contains(Quotes.normalise(
                    variant.parts().get(0).text()))) {
                problem = "it repeats an existing video title";
            }
            if (problem != null) {
                problems.add("variant '" + variant.label() + "': " + problem);
                dropped++;
                continue;
            }
            if (seen.add(Quotes.normalise(variant.parts().get(0).text()))) {
                kept.add(variant);
            }
        }

        if (strict && !problems.isEmpty()) {
            throw new InvalidAiOutputException("These variants cannot be used: "
                    + String.join("; ", problems.subList(0, Math.min(3, problems.size()))) + ".");
        }
        if (kept.isEmpty()) {
            throw new InvalidAiOutputException("No variant could be used: each must stay within the owner's facts.");
        }
        return new Output(List.copyOf(kept), AiJson.strings(root, "notes", 0, 4, 300), dropped);
    }

    private static List<Part> parts(JsonNode variant, Spec spec) {
        JsonNode arr = AiJson.array(variant, "parts", 1, spec.parts().size());
        Map<String, Part> byName = new LinkedHashMap<>();
        for (JsonNode p : arr) {
            if (!p.isObject()) {
                throw new InvalidAiOutputException("Every item in 'parts' must be an object.");
            }
            String name = AiJson.str(p, "name", 1, 40);
            PartSpec ps = spec.part(name).orElseThrow(() -> new InvalidAiOutputException(
                    "'" + name + "' is not a part of " + spec.label() + ". Use only: "
                            + String.join(", ", spec.parts().stream().map(PartSpec::name).toList()) + "."));
            if (byName.containsKey(name)) {
                throw new InvalidAiOutputException("The part '" + name + "' appears more than once in a variant.");
            }
            String value = AiJson.str(p, "text", ps.min(), ps.max());
            byName.put(name, new Part(name, value));
        }
        for (PartSpec ps : spec.parts()) {
            if (ps.required() && !byName.containsKey(ps.name())) {
                throw new InvalidAiOutputException("Every variant needs the part '" + ps.name() + "'.");
            }
        }
        // Parts come back in the spec's order, whatever order the model wrote them in.
        List<Part> ordered = new ArrayList<>();
        for (PartSpec ps : spec.parts()) {
            if (byName.containsKey(ps.name())) {
                ordered.add(byName.get(ps.name()));
            }
        }
        return List.copyOf(ordered);
    }
}
