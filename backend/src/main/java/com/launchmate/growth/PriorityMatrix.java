package com.launchmate.growth;

/**
 * Priority is not chosen by the model. It follows from two qualitative ratings, so the ranking is
 * transparent: high-impact work that is cheap goes first, and low-impact work that is costly last.
 *
 * <pre>
 *                effort LOW   MEDIUM   HIGH
 *   impact HIGH      HIGH      HIGH    MEDIUM
 *   impact MEDIUM    MEDIUM    MEDIUM  LOW
 *   impact LOW       LOW       LOW     LOW
 * </pre>
 */
public final class PriorityMatrix {

    private PriorityMatrix() {}

    public static String priority(String impact, String effort) {
        return switch (impact) {
            case "HIGH" -> effort.equals("HIGH") ? "MEDIUM" : "HIGH";
            case "MEDIUM" -> effort.equals("HIGH") ? "LOW" : "MEDIUM";
            default -> "LOW";
        };
    }

    /** Lower sorts first: priority, then impact, then the cheaper effort (LOW effort 0, HIGH effort 2). */
    public static int rank(String priority, String impact, String effort) {
        return level(priority) * 100 + level(impact) * 10 + (2 - level(effort));
    }

    private static int level(String v) {
        return switch (v) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}
