package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.website.GeoReadiness.Context;
import com.launchmate.website.GeoReadiness.Signal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoReadinessTest {

    private static PageData page(String url, String title, String text, boolean structured, int words) {
        return new PageData(url, 200, title, "", "", "en", true, false, List.of(), List.of(), List.of(), 0, 0, 0, 0,
                words, text, "", structured, 100, 1000, false, List.of());
    }

    private static WebsiteCrawler.Result crawl(boolean robots, List<String> blocked, Integer sitemap, PageData... pages) {
        return new WebsiteCrawler.Result("example.com", URI.create("https://example.com/"), List.of(pages), List.of(),
                robots, List.of(), 0, blocked, sitemap);
    }

    private static Signal sig(List<Signal> all, String key) {
        return all.stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void measuresWhatIsOnTheSiteAndNeverGuessesTheRest() {
        List<Signal> s = GeoReadiness.evaluate(
                crawl(true, List.of("GPTBot"), 0, page("https://example.com/", "Bare Botanics", "Plant-based skincare in Bengaluru", false, 300),
                        page("https://example.com/p", "Cleanser", "short", false, 40)),
                new Context("Bare Botanics", "Bengaluru, India", List.of("face cleanser", "skincare")));

        assertEquals("FAIL", sig(s, "AI_CRAWLERS").status());
        assertTrue(sig(s, "AI_CRAWLERS").detail().contains("GPTBot"));
        assertEquals("FAIL", sig(s, "SITEMAP").status());
        assertEquals("FAIL", sig(s, "STRUCTURED_DATA").status());
        assertEquals("PASS", sig(s, "NAME_ON_SITE").status());
        assertEquals("PASS", sig(s, "OFFER_ON_SITE").status());
        assertEquals("PASS", sig(s, "LOCATION_ON_SITE").status());
        assertEquals("FAIL", sig(s, "THIN_PAGES").status());
        assertEquals("NOT_ANALYSED", sig(s, "CONSISTENCY").status());
        assertEquals("UNAVAILABLE", sig(s, "MENTIONS").status());
    }

    @Test
    void missingInformationIsUnavailableNotFailed() {
        List<Signal> s = GeoReadiness.evaluate(
                crawl(false, List.of(), null, page("https://example.com/", "Home", "hello", true, 5)),
                new Context("", "Global", List.of()));
        assertEquals("PASS", sig(s, "AI_CRAWLERS").status());
        assertEquals("UNAVAILABLE", sig(s, "SITEMAP").status());
        assertEquals("PASS", sig(s, "STRUCTURED_DATA").status());
        assertEquals("UNAVAILABLE", sig(s, "NAME_ON_SITE").status());
        assertEquals("UNAVAILABLE", sig(s, "OFFER_ON_SITE").status());
        assertEquals("UNAVAILABLE", sig(s, "LOCATION_ON_SITE").status());
        assertEquals("UNAVAILABLE", sig(s, "THIN_PAGES").status());
        assertFalse(GeoReadiness.summary(s).isBlank());
    }

    @Test
    void wwwIsOnlyCheckedForRegisteredDomains() {
        assertTrue(DomainHealth.wwwApplies("example.com"));
        assertTrue(DomainHealth.wwwApplies("example.co.in"));
        assertFalse(DomainHealth.wwwApplies("news.ycombinator.com"));
    }
}
