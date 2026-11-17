package com.launchmate.website;

import com.launchmate.website.ReadinessChecks.Check;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the plain-text evidence the model reads. It is plain text, not JSON, so the model can quote
 * lines exactly and the validator can then confirm that every quote really appears here. Page text
 * is included verbatim and may contain instructions aimed at an automated reader; the system prompt
 * tells the model to treat all of it as data.
 */
public final class AuditEvidence {

    public record Evidence(String text, Set<String> pageUrls) {}

    private static final int HOME_TEXT_CHARS = 3000;
    private static final int PAGE_TEXT_CHARS = 1200;

    private AuditEvidence() {}

    public static Evidence build(WebsiteCrawler.Result crawl, List<Check> checks, PageSpeedClient.Result psi,
                                 int maxPages) {
        return build(crawl, checks, psi, maxPages, List.of());
    }

    /**
     * {@code ownerFacts} are short "label: value" lines the owner typed into their profile. They are
     * included, clearly marked as unverified, so suggested copy can use what the owner says the
     * business offers.
     */
    public static Evidence build(WebsiteCrawler.Result crawl, List<Check> checks, PageSpeedClient.Result psi,
                                 int maxPages, List<String> ownerFacts) {
        StringBuilder sb = new StringBuilder();
        Set<String> urls = new LinkedHashSet<>();

        sb.append("SITE: ").append(crawl.startUri()).append(" (host: ").append(crawl.host()).append(")\n");
        sb.append("CRAWL: ").append(crawl.pages().size()).append(" page(s) audited (limit ").append(maxPages)
                .append("). robots.txt: ").append(crawl.robotsFound() ? "found" : "not found").append(".\n");
        for (String w : crawl.warnings()) {
            sb.append("- crawl note: ").append(line(w)).append("\n");
        }

        if (!ownerFacts.isEmpty()) {
            sb.append("\nOWNER-PROVIDED INFORMATION (typed in by the owner; not verified):\n");
            for (String fact : ownerFacts) {
                String f = line(fact);
                sb.append("- ").append(f.length() > 400 ? f.substring(0, 400) : f).append("\n");
            }
        }

        sb.append("\nREADINESS CHECKS (measured on the homepage):\n");
        for (Check c : checks) {
            String verdict = c.passed() == null ? "NOT ASSESSED" : c.passed() ? "PASS" : "FAIL";
            sb.append("- ").append(c.key()).append(": ").append(verdict).append(" - ").append(line(c.detail()))
                    .append("\n");
        }

        sb.append("\n");
        if (psi.available()) {
            sb.append("PAGESPEED (mobile; Google PageSpeed Insights; measured ").append(psi.measuredAt()).append("):\n");
            score(sb, psi, "performance", "performance score");
            score(sb, psi, "accessibility", "accessibility score");
            score(sb, psi, "bestPractices", "best practices score");
            score(sb, psi, "seo", "seo score");
            metric(sb, psi, "firstContentfulPaint", "first contentful paint");
            metric(sb, psi, "largestContentfulPaint", "largest contentful paint");
            metric(sb, psi, "totalBlockingTime", "total blocking time");
            metric(sb, psi, "cumulativeLayoutShift", "cumulative layout shift");
            metric(sb, psi, "speedIndex", "speed index");
            for (String issue : psi.issues()) {
                sb.append("- failing PageSpeed audit: ").append(line(issue)).append("\n");
            }
            if (psi.fieldDataCategory() != null) {
                sb.append("- real-user data (Chrome UX Report) overall: ").append(psi.fieldDataCategory()).append("\n");
            }
        } else {
            sb.append("PAGESPEED: not available (").append(line(psi.error() == null ? "unknown reason" : psi.error()))
                    .append(")\n");
        }

        int n = 0;
        for (PageData p : crawl.pages()) {
            n++;
            urls.add(p.url());
            sb.append("\nPAGE ").append(n).append(": ").append(p.url()).append("\n");
            sb.append("  http status: ").append(p.status()).append("\n");
            if (p.truncated()) {
                sb.append("  read: only the first part of this page (it is larger than the size limit)\n");
            }
            sb.append("  title: ").append(orNone(p.title())).append("\n");
            sb.append("  meta description: ").append(orNone(p.metaDescription())).append("\n");
            sb.append("  h1: ").append(orNone(String.join(" | ", p.h1()))).append("\n");
            sb.append("  h2: ").append(orNone(String.join(" | ", p.h2()))).append("\n");
            sb.append("  navigation: ").append(orNone(String.join(" | ", p.navigation()))).append("\n");
            sb.append("  images: ").append(p.imageCount()).append(" (").append(p.imagesMissingAlt())
                    .append(" without alt attribute)\n");
            sb.append("  words: ").append(p.wordCount()).append("\n");
            int limit = n == 1 ? HOME_TEXT_CHARS : PAGE_TEXT_CHARS;
            String text = p.text().length() > limit ? p.text().substring(0, limit) : p.text();
            sb.append("  text: ").append(orNone(line(text))).append("\n");
        }
        return new Evidence(sb.toString(), Set.copyOf(urls));
    }

    private static void score(StringBuilder sb, PageSpeedClient.Result psi, String key, String label) {
        Integer v = psi.scores().get(key);
        if (v != null) {
            sb.append("- ").append(label).append(": ").append(v).append("\n");
        }
    }

    private static void metric(StringBuilder sb, PageSpeedClient.Result psi, String key, String label) {
        String v = psi.metrics().get(key);
        if (v != null) {
            sb.append("- ").append(label).append(": ").append(line(v)).append("\n");
        }
    }

    private static String line(String s) {
        return PageExtractor.collapse(s);
    }

    private static String orNone(String s) {
        return s == null || s.isBlank() ? "(none)" : s;
    }
}
