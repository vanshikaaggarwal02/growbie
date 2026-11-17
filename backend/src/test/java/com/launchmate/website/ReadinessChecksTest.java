package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.website.ReadinessChecks.Check;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadinessChecksTest {

    private static Check find(List<Check> checks, String key) {
        return checks.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void aWellFormedPageWithGoodScoresPassesEveryCheck() {
        List<Check> checks = ReadinessChecks.evaluate(TestPages.healthy(), URI.create("https://example.com/"),
                TestPages.psi(95, 96, 100, 92));
        assertTrue(checks.stream().allMatch(c -> Boolean.TRUE.equals(c.passed())),
                checks.stream().filter(c -> !Boolean.TRUE.equals(c.passed())).toList().toString());
        assertEquals("15 of 15 checks passed", ReadinessChecks.summary(checks));
    }

    @Test
    void aPoorPageFailsTheRightChecksAndUnavailableDataIsNeitherPassNorFail() {
        PageSpeedClient.Result unavailable = new PageSpeedClient.Result(false, "mobile", java.util.Map.of(),
                java.util.Map.of(), List.of(), null, null, "PageSpeed did not respond.");
        List<Check> checks = ReadinessChecks.evaluate(TestPages.poor(), URI.create("http://example.com/"), unavailable);

        assertTrue(find(checks, "TITLE_PRESENT").passed());
        assertFalse(find(checks, "TITLE_SPECIFIC").passed(), "\"Home\" is a generic title");
        assertFalse(find(checks, "TITLE_LENGTH").passed());
        assertFalse(find(checks, "META_DESCRIPTION_PRESENT").passed());
        assertNull(find(checks, "META_DESCRIPTION_LENGTH").passed(), "no description to measure");
        assertFalse(find(checks, "SINGLE_H1").passed());
        assertFalse(find(checks, "VIEWPORT").passed());
        assertFalse(find(checks, "LANGUAGE").passed());
        assertFalse(find(checks, "IMAGES_ALT").passed());
        assertFalse(find(checks, "HTTPS").passed());
        assertFalse(find(checks, "INDEXABLE").passed());
        for (String k : List.of("PAGESPEED_PERFORMANCE", "PAGESPEED_ACCESSIBILITY", "PAGESPEED_BEST_PRACTICES",
                "PAGESPEED_SEO")) {
            assertNull(find(checks, k).passed(), k + " must be unassessed when PageSpeed is unavailable");
        }
        assertEquals("1 of 10 checks passed (5 could not be assessed)", ReadinessChecks.summary(checks));
    }

    @Test
    void aMissingTitleIsReportedOnceAndDependentChecksAreUnassessed() {
        PageData noTitle = TestPages.page("https://example.com/", "", "some words here");
        List<Check> checks = ReadinessChecks.evaluate(noTitle, URI.create("https://example.com/"),
                TestPages.psi(95, 95, 95, 95));
        assertFalse(find(checks, "TITLE_PRESENT").passed());
        assertNull(find(checks, "TITLE_SPECIFIC").passed());
        assertNull(find(checks, "TITLE_LENGTH").passed());
    }

    @Test
    void pageSpeedScoresBelowNinetyFailAndTheDetailStatesTheScore() {
        List<Check> checks = ReadinessChecks.evaluate(TestPages.healthy(), URI.create("https://example.com/"),
                TestPages.psi(62, 96, 100, 89));
        assertFalse(find(checks, "PAGESPEED_PERFORMANCE").passed());
        assertTrue(find(checks, "PAGESPEED_PERFORMANCE").detail().contains("score is 62"));
        assertTrue(find(checks, "PAGESPEED_ACCESSIBILITY").passed());
        assertFalse(find(checks, "PAGESPEED_SEO").passed(), "89 is just below the good threshold");
    }
}
