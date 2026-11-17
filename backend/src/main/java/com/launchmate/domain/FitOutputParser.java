package com.launchmate.domain;

import com.launchmate.ai.AiJson;
import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.domain.DomainFitDtos.Assessment;
import com.launchmate.domain.DomainFitDtos.CurrentDomainReview;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the model's domain assessments against the domains that were actually sent. The model
 * may not assess a domain it was not given, skip one it was given, or review a "current" domain
 * that does not exist.
 */
final class FitOutputParser {

    record FitOutput(Map<String, Assessment> byDomain, CurrentDomainReview currentReview, List<String> limitations) {}

    private static final Set<String> VERDICTS = Set.of("STRONG", "ACCEPTABLE", "WEAK");
    private static final Set<String> RECOMMENDATIONS = Set.of("KEEP_CURRENT", "CHANGE_NEEDS_CAREFUL_REVIEW");

    private FitOutputParser() {}

    static FitOutput parse(ObjectMapper json, String text, Set<String> expectedDomains, String currentDomain) {
        JsonNode root = AiJson.parseObject(json, text);

        Map<String, Assessment> byDomain = new LinkedHashMap<>();
        for (JsonNode a : AiJson.array(root, "assessments", expectedDomains.size(), expectedDomains.size())) {
            if (!a.isObject()) {
                throw new InvalidAiOutputException("Every item in 'assessments' must be an object.");
            }
            String domain = AiJson.str(a, "domain", 1, 253).toLowerCase();
            if (!expectedDomains.contains(domain)) {
                throw new InvalidAiOutputException("'" + domain + "' was not one of the domains given.");
            }
            if (byDomain.containsKey(domain)) {
                throw new InvalidAiOutputException("'" + domain + "' was assessed more than once.");
            }
            byDomain.put(domain, new Assessment(
                    AiJson.oneOf(a, "verdict", VERDICTS),
                    AiJson.strings(a, "strengths", 0, 4, 200),
                    AiJson.strings(a, "concerns", 0, 4, 200),
                    AiJson.str(a, "suggestion", 1, 300)));
        }
        if (byDomain.size() != expectedDomains.size()) {
            throw new InvalidAiOutputException("Every domain given must have exactly one assessment.");
        }

        CurrentDomainReview review = null;
        JsonNode reviews = AiJson.array(root, "currentDomainReviews", 0, 1);
        if (reviews.size() == 1) {
            JsonNode r = reviews.get(0);
            if (currentDomain == null) {
                throw new InvalidAiOutputException(
                        "'currentDomainReviews' must be empty because no domain was marked as current.");
            }
            String domain = AiJson.str(r, "domain", 1, 253).toLowerCase();
            if (!domain.equals(currentDomain)) {
                throw new InvalidAiOutputException("The current-domain review must be for '" + currentDomain + "'.");
            }
            review = new CurrentDomainReview(domain,
                    AiJson.oneOf(r, "recommendation", RECOMMENDATIONS),
                    AiJson.str(r, "reasoning", 1, 500),
                    AiJson.strings(r, "risks", 4, 6, 300),
                    AiJson.strings(r, "checklist", 4, 10, 250));
        } else if (currentDomain != null) {
            throw new InvalidAiOutputException(
                    "'currentDomainReviews' must contain one review for the current domain '" + currentDomain + "'.");
        }

        return new FitOutput(Map.copyOf(byDomain), review, AiJson.strings(root, "limitations", 0, 5, 300));
    }
}
