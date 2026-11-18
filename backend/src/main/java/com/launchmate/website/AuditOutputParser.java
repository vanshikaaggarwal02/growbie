package com.launchmate.website;

import com.launchmate.ai.AiJson;
import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.ai.Quotes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the model's audit and, above all, checks every finding against the evidence that was
 * sent. A finding is kept only if its page address is one of the audited pages and its quoted
 * evidence really appears in the evidence text. That is what stops invented problems and invented
 * numbers from reaching the owner.
 *
 * <p>In strict mode (the first attempt) any unverifiable finding fails the reply so the model can
 * try again with the reason. In lenient mode (the retry) unverifiable findings are dropped and
 * counted, so the owner still gets every finding that can be trusted.
 */
final class AuditOutputParser {

    record Finding(String category, String severity, String pageUrl, String title, String evidence,
                   String impact, String recommendation, String suggestedCopy, String basis) {}

    record Output(String summary, List<Finding> findings, List<String> limitations, int dropped) {}

    private static final Set<String> CATEGORIES = Set.of(
            "SEO", "CONTENT", "CONVERSION", "PERFORMANCE", "ACCESSIBILITY", "TRUST", "TECHNICAL", "BRAND");
    private static final Set<String> SEVERITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> BASES = Set.of("OBSERVED", "INFERENCE");
    private static final int MIN_EVIDENCE = 4;
    private static final int MAX_EVIDENCE = 300;

    private AuditOutputParser() {}

    static Output parse(ObjectMapper json, String text, Set<String> pageUrls, String evidenceText,
                        boolean strict) {
        JsonNode root = AiJson.parseObject(json, text);
        String summary = AiJson.str(root, "summary", 1, 600);
        String haystack = normalise(evidenceText);

        List<Finding> kept = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        int dropped = 0;

        for (JsonNode f : AiJson.array(root, "findings", 0, 12)) {
            if (!f.isObject()) {
                throw new InvalidAiOutputException("Every item in 'findings' must be an object.");
            }
            // Structure and limits are always enforced.
            Finding finding = new Finding(
                    AiJson.oneOf(f, "category", CATEGORIES),
                    AiJson.oneOf(f, "severity", SEVERITIES),
                    AiJson.str(f, "pageUrl", 1, 2048),
                    AiJson.str(f, "title", 1, 120),
                    // Parse a bounded oversized quote so lenient retry can omit just this finding,
                    // instead of failing the entire audit for a single length violation.
                    AiJson.str(f, "evidence", MIN_EVIDENCE, 1000),
                    AiJson.str(f, "impact", 1, 400),
                    AiJson.str(f, "recommendation", 1, 500),
                    AiJson.str(f, "suggestedCopy", 0, 400),
                    AiJson.oneOf(f, "basis", BASES));

            String problem = null;
            if (finding.evidence().length() > MAX_EVIDENCE) {
                problem = "its evidence is longer than " + MAX_EVIDENCE + " characters";
            } else if (!pageUrls.contains(finding.pageUrl())) {
                problem = "its pageUrl '" + finding.pageUrl() + "' is not one of the audited pages";
            } else if (!haystack.contains(normalise(finding.evidence()))) {
                problem = "its evidence is not copied exactly from one line of the EVIDENCE";
            }
            if (problem != null) {
                problems.add("'" + finding.title() + "': " + problem);
                dropped++;
                continue;
            }
            if (titles.add(finding.title().toLowerCase(Locale.ROOT))) {
                kept.add(finding);
            }
        }

        if (strict && !problems.isEmpty()) {
            throw new InvalidAiOutputException("These findings could not be verified against the evidence: "
                    + String.join("; ", problems.subList(0, Math.min(3, problems.size()))) + ".");
        }
        return new Output(summary, List.copyOf(kept), AiJson.strings(root, "limitations", 0, 6, 300), dropped);
    }

    static String normalise(String s) {
        return Quotes.normalise(s);
    }
}
