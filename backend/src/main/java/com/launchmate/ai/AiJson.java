package com.launchmate.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Strict readers for model output. Every failure raises {@link InvalidAiOutputException} with a
 * message precise enough to send back to the model for one corrective retry.
 */
public final class AiJson {

    private AiJson() {}

    public static JsonNode parseObject(ObjectMapper json, String text) {
        JsonNode node;
        try {
            node = json.readTree(text == null ? "" : text.trim());
        } catch (RuntimeException e) {
            throw new InvalidAiOutputException("The reply was not valid JSON.");
        }
        if (node == null || !node.isObject()) {
            throw new InvalidAiOutputException("The reply must be one JSON object.");
        }
        return node;
    }

    public static String str(JsonNode parent, String field, int minLen, int maxLen) {
        JsonNode n = parent.get(field);
        if (n == null || !n.isString()) {
            throw new InvalidAiOutputException("'" + field + "' must be a string.");
        }
        String s = n.asString().trim();
        if (s.length() < minLen || s.length() > maxLen) {
            throw new InvalidAiOutputException("'" + field + "' must be between " + minLen + " and "
                    + maxLen + " characters (it was " + s.length() + ").");
        }
        return s;
    }

    public static int integer(JsonNode parent, String field, int min, int max) {
        JsonNode n = parent.get(field);
        if (n == null || !n.isIntegralNumber() || !n.canConvertToInt()) {
            throw new InvalidAiOutputException("'" + field + "' must be a whole number.");
        }
        int v = n.asInt();
        if (v < min || v > max) {
            throw new InvalidAiOutputException("'" + field + "' must be between " + min + " and " + max + ".");
        }
        return v;
    }

    public static JsonNode array(JsonNode parent, String field, int minItems, int maxItems) {
        JsonNode n = parent.get(field);
        if (n == null || !n.isArray()) {
            throw new InvalidAiOutputException("'" + field + "' must be an array.");
        }
        if (n.size() < minItems || n.size() > maxItems) {
            throw new InvalidAiOutputException("'" + field + "' must have between " + minItems + " and "
                    + maxItems + " items (it had " + n.size() + ").");
        }
        return n;
    }

    public static List<String> strings(JsonNode parent, String field, int minItems, int maxItems, int maxLen) {
        JsonNode arr = array(parent, field, minItems, maxItems);
        List<String> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (!item.isString() || item.asString().isBlank() || item.asString().trim().length() > maxLen) {
                throw new InvalidAiOutputException("Every item in '" + field
                        + "' must be a non-empty string of at most " + maxLen + " characters.");
            }
            out.add(item.asString().trim());
        }
        return List.copyOf(out);
    }

    public static String oneOf(JsonNode parent, String field, Set<String> allowed) {
        String s = str(parent, field, 1, 80);
        if (!allowed.contains(s)) {
            throw new InvalidAiOutputException("'" + field + "' must be one of " + allowed + " (it was '" + s + "').");
        }
        return s;
    }
}
