package com.launchmate.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two checks that stop generated copy from making things up. Both compare the generated text with
 * the guard text, which is everything the owner supplied or LaunchMate measured:
 * <ul>
 *   <li>Numbers: a percentage, a price, a decimal or any whole number above ten must appear in the
 *       guard text. Small counts such as "5 tips" and common video lengths (15, 30, 45, 60 and 90
 *       seconds) are allowed.</li>
 *   <li>Claims: words that promise or certify something ("guaranteed", "clinically proven",
 *       "award-winning") are refused unless the owner used them first.</li>
 * </ul>
 */
public final class ContentGuards {

    private static final Pattern NUMBER =
            Pattern.compile("(\\p{Sc})?\\s?(\\d[\\d,]*(?:\\.\\d+)?)(\\s?%|\\s+percent\\b)?", Pattern.CASE_INSENSITIVE);
    private static final int SMALL_COUNT = 10;
    private static final Set<String> COMMON_DURATIONS = Set.of("15", "30", "45", "60", "90");
    private static final List<String> RISKY = List.of(
            "guarantee", "guaranteed", "clinically", "proven", "#1", "number one", "best-selling", "bestselling",
            "award-winning", "award winning", "certified", "risk-free", "risk free", "cure", "miracle", "doctor",
            "dermatologist", "fda", "lab-tested", "lab tested");

    private ContentGuards() {}

    /** Numbers in {@code generated} that the owner never supplied, as written. */
    public static List<String> unsupportedNumbers(String generated, String guardText) {
        Set<String> mentioned = new HashSet<>();
        Set<String> mentionedPercent = new HashSet<>();
        Matcher g = NUMBER.matcher(guardText);
        while (g.find()) {
            mentioned.add(digits(g.group(2)));
            if (g.group(3) != null) {
                mentionedPercent.add(digits(g.group(2)));
            }
        }
        List<String> offending = new ArrayList<>();
        Matcher m = NUMBER.matcher(generated);
        while (m.find()) {
            String n = digits(m.group(2));
            boolean allowed;
            if (m.group(3) != null) {
                // A percentage needs a percentage: "a 30 second demo" does not support "30% off".
                allowed = mentionedPercent.contains(n);
            } else if (m.group(1) != null || n.contains(".")) {
                allowed = mentioned.contains(n);
            } else {
                allowed = mentioned.contains(n) || COMMON_DURATIONS.contains(n) || isSmall(n);
            }
            String written = m.group().trim();
            if (!allowed && !offending.contains(written)) {
                offending.add(written);
            }
        }
        return offending;
    }

    /** Risky promise or certification words in {@code generated} that the owner never used. */
    public static List<String> unsupportedClaims(String generated, String guardText) {
        String text = generated.toLowerCase(Locale.ROOT);
        String guard = guardText.toLowerCase(Locale.ROOT);
        List<String> offending = new ArrayList<>();
        for (String phrase : RISKY) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])");
            if (p.matcher(text).find() && !p.matcher(guard).find()) {
                offending.add(phrase);
            }
        }
        return offending;
    }

    private static String digits(String raw) {
        return raw.replace(",", "");
    }

    private static boolean isSmall(String n) {
        return n.length() <= 2 && Integer.parseInt(n) <= SMALL_COUNT;
    }
}
