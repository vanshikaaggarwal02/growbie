package com.launchmate.ai;

import java.util.Locale;

/**
 * Checks that something the model "quoted" really appears in the evidence it was given. Matching is
 * tolerant of case, spacing (including non-breaking spaces) and typographic quote and dash styles
 * only. It is not tolerant of different words.
 */
public final class Quotes {

    private Quotes() {}

    public static String normalise(String s) {
        String t = collapse(s)
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .toLowerCase(Locale.ROOT);
        // A quote copied with surrounding quotation marks is still the same quote.
        while (t.length() > 1 && (t.startsWith("\"") || t.startsWith("'")) && (t.endsWith("\"") || t.endsWith("'"))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    /** {@code normalisedEvidence} must already have been passed through {@link #normalise}. */
    public static boolean appearsIn(String normalisedEvidence, String quote) {
        String q = normalise(quote);
        return !q.isEmpty() && normalisedEvidence.contains(q);
    }

    /** One plain space for every kind of whitespace, with zero-width spaces removed. */
    public static String collapse(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("​", "").replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
}
