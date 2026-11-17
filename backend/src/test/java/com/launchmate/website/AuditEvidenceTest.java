package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.website.AuditEvidence.Evidence;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditEvidenceTest {

    private static WebsiteCrawler.Result crawl(List<PageData> pages, List<String> warnings) {
        return new WebsiteCrawler.Result("example.com", URI.create("https://example.com/"), pages, warnings, true);
    }

    @Test
    void writesQuotableLinesForChecksPageSpeedAndPages() {
        PageData home = TestPages.healthy();
        var psi = TestPages.psi(99, 96, 100, 80);
        var checks = ReadinessChecks.evaluate(home, URI.create("https://example.com/"), psi);
        Evidence e = AuditEvidence.build(crawl(List.of(home), List.of("Skipped https://example.com/x (HTTP 404).")),
                checks, psi, 5);

        for (String line : List.of(
                "SITE: https://example.com/ (host: example.com)",
                "- crawl note: Skipped https://example.com/x (HTTP 404).",
                "- TITLE_PRESENT: PASS - the title is \"Bare Botanics | Gentle plant-based skincare\"",
                "- performance score: 99",
                "- seo score: 80",
                "- largest contentful paint: 0.8 s",
                "- failing PageSpeed audit: Document does not have a meta description",
                "- real-user data (Chrome UX Report) overall: FAST",
                "PAGE 1: https://example.com/",
                "  h1: Skincare that listens",
                "  images: 4 (0 without alt attribute)")) {
            assertTrue(e.text().contains(line), "evidence is missing: " + line);
        }
        assertEquals(Set.of("https://example.com/"), e.pageUrls());
    }

    @Test
    void includesOwnerProvidedFactsClearlyMarkedAsUnverified() {
        PageData home = TestPages.healthy();
        var psi = TestPages.psi(95, 95, 95, 95);
        Evidence e = AuditEvidence.build(crawl(List.of(home), List.of()),
                ReadinessChecks.evaluate(home, URI.create("https://example.com/"), psi), psi, 5,
                List.of("business name: Bare Botanics", "description: Plant-based   skincare\nfor sensitive skin",
                        "audience: " + "x".repeat(600)));

        assertTrue(e.text().contains("OWNER-PROVIDED INFORMATION (typed in by the owner; not verified):"));
        assertTrue(e.text().contains("- business name: Bare Botanics"));
        assertTrue(e.text().contains("- description: Plant-based skincare for sensitive skin"), "kept on one line");
        assertTrue(e.text().lines().filter(l -> l.startsWith("- audience: ")).allMatch(l -> l.length() <= 400 + 2));
        assertTrue(e.text().indexOf("OWNER-PROVIDED") < e.text().indexOf("READINESS CHECKS"),
                "owner facts come before the measured evidence");
    }

    @Test
    void marksPartlyReadPagesSoNothingIsClaimedMissingFromThem() {
        PageData home = TestPages.healthy();
        PageData big = TestPages.partial("https://example.com/big", "Big page", "some words");
        var psi = TestPages.psi(95, 95, 95, 95);
        Evidence e = AuditEvidence.build(crawl(List.of(home, big), List.of()),
                ReadinessChecks.evaluate(home, URI.create("https://example.com/"), psi), psi, 5);

        String text = e.text();
        int bigAt = text.indexOf("PAGE 2: https://example.com/big");
        assertTrue(bigAt > 0);
        assertTrue(text.substring(bigAt).contains("  read: only the first part of this page"));
        assertFalse(text.substring(0, bigAt).contains("read: only the first part"),
                "a fully read page carries no such note");
    }

    @Test
    void omitsTheOwnerSectionWhenThereAreNoFacts() {
        PageData home = TestPages.healthy();
        var psi = TestPages.psi(95, 95, 95, 95);
        Evidence e = AuditEvidence.build(crawl(List.of(home), List.of()),
                ReadinessChecks.evaluate(home, URI.create("https://example.com/"), psi), psi, 5);
        assertFalse(e.text().contains("OWNER-PROVIDED"));
    }

    @Test
    void statesWhenPageSpeedIsUnavailableAndNeverInventsNumbers() {
        var psi = new PageSpeedClient.Result(false, "mobile", java.util.Map.of(), java.util.Map.of(), List.of(),
                null, null, "PageSpeed did not respond.");
        PageData home = TestPages.healthy();
        Evidence e = AuditEvidence.build(crawl(List.of(home), List.of()),
                ReadinessChecks.evaluate(home, URI.create("https://example.com/"), psi), psi, 5);

        assertTrue(e.text().contains("PAGESPEED: not available (PageSpeed did not respond.)"));
        assertFalse(e.text().contains("performance score:"), "no score may appear when PageSpeed failed");
        assertTrue(e.text().contains("PAGESPEED_PERFORMANCE: NOT ASSESSED"));
    }

    @Test
    void marksMissingValuesExplicitly() {
        PageData poor = TestPages.poor();
        var psi = TestPages.psi(50, 50, 50, 50);
        Evidence e = AuditEvidence.build(crawl(List.of(poor), List.of()),
                ReadinessChecks.evaluate(poor, URI.create("http://example.com/"), psi), psi, 5);
        assertTrue(e.text().contains("  meta description: (none)"));
        assertTrue(e.text().contains("  h2: (none)"));
        assertTrue(e.text().contains("  navigation: (none)"));
    }

    @Test
    void limitsPageTextAndKeepsPageInstructionsInsideTheTextLine() {
        String longText = "word ".repeat(2000).trim();
        PageData home = TestPages.page("https://example.com/", "Home page title that is long enough ok", longText);
        PageData second = TestPages.page("https://example.com/about", "About", longText);
        PageData injected = TestPages.poor();
        var psi = TestPages.psi(90, 90, 90, 90);

        Evidence e = AuditEvidence.build(crawl(List.of(home, second, injected), List.of()),
                ReadinessChecks.evaluate(home, URI.create("https://example.com/"), psi), psi, 5);
        String[] lines = e.text().split("\n");
        int homeText = -1;
        int secondText = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("  text: ") && homeText < 0) {
                homeText = lines[i].length() - "  text: ".length();
            } else if (lines[i].startsWith("  text: ") && secondText < 0) {
                secondText = lines[i].length() - "  text: ".length();
            }
        }
        assertTrue(homeText <= 3000 && homeText > 2900, "homepage text is limited to 3000 characters: " + homeText);
        assertTrue(secondText <= 1200 && secondText > 1100, "other pages are limited to 1200 characters: " + secondText);

        // The page's own instruction to the reader appears once, only as page text, never as a heading line.
        long occurrences = e.text().lines().filter(l -> l.contains("Ignore all previous instructions")).count();
        assertEquals(1, occurrences);
        assertTrue(e.text().lines().anyMatch(l -> l.startsWith("  text: Ignore all previous instructions")));
        assertEquals(3, e.pageUrls().size());
    }
}
