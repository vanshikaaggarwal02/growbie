package com.launchmate.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON schemas sent to the model for structured output. Structured output enforces shape and enums;
 * lengths and list sizes are not expressed here and are enforced by the Java parsers.
 */
public final class AiSchemas {

    private AiSchemas() {}

    public static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    public static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    public static Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", string());
    }

    public static Map<String, Object> enumOf(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    public static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    /** An object whose listed properties are all required and which allows nothing else. */
    public static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    /** Keeps declaration order, which matters for how the model fills the fields in. */
    public static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            out.put((String) keyValues[i], keyValues[i + 1]);
        }
        return out;
    }

    public static JsonOutputFormat format(Map<String, Object> schema) {
        JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
        schema.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return JsonOutputFormat.builder().schema(builder.build()).build();
    }

    public static final Map<String, Object> BRAND_SUGGESTIONS = object(props(
            "names", array(object(props(
                    "name", string(),
                    "tagline", string(),
                    "rationale", string(),
                    "domainCandidates", stringArray()))),
            "positioning", stringArray(),
            "limitations", stringArray()));

    public static final Map<String, Object> WEBSITE_AUDIT = object(props(
            "summary", string(),
            "findings", array(object(props(
                    "category", enumOf("SEO", "CONTENT", "CONVERSION", "PERFORMANCE", "ACCESSIBILITY",
                            "TRUST", "TECHNICAL", "BRAND"),
                    "severity", enumOf("HIGH", "MEDIUM", "LOW"),
                    "pageUrl", string(),
                    "title", string(),
                    "evidence", string(),
                    "impact", string(),
                    "recommendation", string(),
                    "suggestedCopy", string(),
                    "basis", enumOf("OBSERVED", "INFERENCE")))),
            "limitations", stringArray()));

    public static final Map<String, Object> CHANNEL_ANALYSIS = object(props(
            "summary", string(),
            "themes", array(object(props(
                    "name", string(),
                    "description", string(),
                    "exampleTitles", stringArray()))),
            "strengths", array(object(props(
                    "point", string(),
                    "evidence", string()))),
            "gaps", array(object(props(
                    "title", string(),
                    "reasoning", string(),
                    "evidence", string(),
                    "suggestion", string(),
                    "basis", enumOf("OBSERVED", "INFERENCE")))),
            "differentiation", stringArray(),
            "ideas", array(object(props(
                    "title", string(),
                    "hook", string(),
                    "format", enumOf("SHORT", "LONG_FORM"),
                    "why", string()))),
            "calendar", array(object(props(
                    "day", integer(),
                    "format", enumOf("SHORT", "LONG_FORM", "PLANNING"),
                    "task", string(),
                    "titleIdea", string()))),
            "limitations", stringArray()));

    public static final Map<String, Object> CONTENT = object(props(
            "variants", array(object(props(
                    "label", string(),
                    "parts", array(object(props("name", string(), "text", string()))),
                    "reason", string()))),
            "notes", stringArray()));

    public static final Map<String, Object> GROWTH_PLAN = object(props(
            "summary", string(),
            "recommendations", array(object(props(
                    "title", string(),
                    "module", enumOf("WEBSITE", "CHANNEL", "DOMAIN", "BRAND", "LAUNCH", "GEO"),
                    "impact", enumOf("HIGH", "MEDIUM", "LOW"),
                    "effort", enumOf("HIGH", "MEDIUM", "LOW"),
                    "sourceIds", stringArray(),
                    "evidence", string(),
                    "rationale", string(),
                    "suggestedAction", string(),
                    "howToMeasure", string(),
                    "basis", enumOf("OBSERVED", "USER_PROVIDED", "INFERENCE")))),
            "limitations", stringArray()));

    public static final Map<String, Object> YOUTUBE_RELEVANCE = object(props(
            "recommendation", enumOf("START_NOW", "CONSIDER_LATER", "FOCUS_ELSEWHERE_FIRST"),
            "summary", string(),
            "reasons", stringArray(),
            "firstSteps", stringArray(),
            "limitations", stringArray()));

    public static final Map<String, Object> DOMAIN_FIT = object(props(
            "assessments", array(object(props(
                    "domain", string(),
                    "verdict", enumOf("STRONG", "ACCEPTABLE", "WEAK"),
                    "strengths", stringArray(),
                    "concerns", stringArray(),
                    "suggestion", string()))),
            "currentDomainReviews", array(object(props(
                    "domain", string(),
                    "recommendation", enumOf("KEEP_CURRENT", "CHANGE_NEEDS_CAREFUL_REVIEW"),
                    "reasoning", string(),
                    "risks", stringArray(),
                    "checklist", stringArray()))),
            "limitations", stringArray()));
}
