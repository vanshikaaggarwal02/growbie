package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RobotsTxtTest {

    private static final String AGENT = "growbiebot";

    @Test
    void disallowAndTheLongerAllowOverrideAreApplied() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /private
                Allow: /private/public
                """, AGENT);
        assertTrue(r.allows("/"));
        assertTrue(r.allows("/about"));
        assertFalse(r.allows("/private"));
        assertFalse(r.allows("/private/secret.html"));
        assertTrue(r.allows("/private/public/page"), "the longer Allow rule is more specific");
    }

    @Test
    void aGroupNamingThisCrawlerBeatsTheStarGroup() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: GrowbieBot
                Disallow: /

                User-agent: *
                Allow: /
                """, AGENT);
        assertFalse(r.allows("/"));
        assertFalse(r.allows("/anything"));
    }

    @Test
    void otherCrawlersRulesDoNotApplyToUs() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: Googlebot
                Disallow: /
                """, AGENT);
        assertTrue(r.allows("/"));
    }

    @Test
    void wildcardsAndEndAnchorsWork() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /*.pdf$
                Disallow: /tmp/*/cache
                """, AGENT);
        assertFalse(r.allows("/files/report.pdf"));
        assertTrue(r.allows("/files/report.pdf?download=1"), "$ anchors the end of the path");
        assertFalse(r.allows("/tmp/abc/cache"));
        assertTrue(r.allows("/tmp/abc/other"));
    }

    @Test
    void emptyDisallowCommentsAndSharedGroupsAreHandled() {
        RobotsTxt open = RobotsTxt.parse("User-agent: *\nDisallow:\n", AGENT);
        assertTrue(open.allows("/anything"));

        RobotsTxt shared = RobotsTxt.parse("""
                # keep bots out of the admin area
                User-agent: Googlebot
                User-agent: *
                Disallow: /admin # private
                """, AGENT);
        assertFalse(shared.allows("/admin/login"));
        assertTrue(shared.allows("/blog"));
    }

    @Test
    void allowAllConstantAndMalformedInputAreSafe() {
        assertTrue(RobotsTxt.ALLOW_ALL.allows("/x"));
        RobotsTxt junk = RobotsTxt.parse("<html>not a robots file</html>\n\u0000\u0001", AGENT);
        assertTrue(junk.allows("/x"));
    }
}
