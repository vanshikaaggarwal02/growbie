package com.launchmate.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic facts about a domain name: no AI, no network. These are measured from the string
 * itself, so they are reliable evidence for any later judgement about brand fit.
 * Intended for registrable names such as "barebotanics.com" or "barebotanics.co.in".
 */
public final class DomainAnalyzer {

    public enum BrandMatch { EXACT, CONTAINS_BRAND, CONTAINED_IN_BRAND, SIMILAR, DIFFERENT, NOT_PROVIDED }

    public record DomainProperties(String label, String tld, int length, int hyphens, int digits,
                                   BrandMatch brandMatch, List<String> flags) {}

    private static final Set<String> COMMON_TLDS =
            Set.of("com", "in", "co", "net", "org", "io", "shop", "store", "app", "online", "co.in");
    private static final int LONG_LABEL = 15;
    private static final double SIMILAR_RATIO = 0.7;

    private DomainAnalyzer() {}

    public static DomainProperties analyse(String domain, String brandName) {
        int dot = domain.indexOf('.');
        String label = domain.substring(0, dot);
        String tld = domain.substring(dot + 1);
        int hyphens = (int) label.chars().filter(c -> c == '-').count();
        int digits = (int) label.chars().filter(Character::isDigit).count();
        BrandMatch match = match(label, brandName);

        List<String> flags = new ArrayList<>();
        if (hyphens > 0) {
            flags.add("HAS_HYPHEN");
        }
        if (digits > 0) {
            flags.add("HAS_DIGITS");
        }
        if (label.length() > LONG_LABEL) {
            flags.add("LONG_NAME");
        }
        if (!COMMON_TLDS.contains(tld)) {
            flags.add("UNCOMMON_TLD");
        }
        if (match == BrandMatch.DIFFERENT) {
            flags.add("BRAND_MISMATCH");
        }
        return new DomainProperties(label, tld, label.length(), hyphens, digits, match, List.copyOf(flags));
    }

    private static BrandMatch match(String label, String brandName) {
        String brand = brandName == null ? "" : brandName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (brand.isEmpty()) {
            return BrandMatch.NOT_PROVIDED;
        }
        String compact = label.replace("-", "");
        if (compact.equals(brand)) {
            return BrandMatch.EXACT;
        }
        if (compact.contains(brand)) {
            return BrandMatch.CONTAINS_BRAND;
        }
        if (compact.length() >= 3 && brand.contains(compact)) {
            return BrandMatch.CONTAINED_IN_BRAND;
        }
        int distance = levenshtein(compact, brand);
        double ratio = 1.0 - (double) distance / Math.max(compact.length(), brand.length());
        return ratio >= SIMILAR_RATIO ? BrandMatch.SIMILAR : BrandMatch.DIFFERENT;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}
