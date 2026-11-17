package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.website.WebsiteAuditDtos.AuditView;
import com.launchmate.website.WebsiteAuditDtos.FindingView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuditComparisonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static FindingView finding(String category, String title, String evidence) {
        return new FindingView(UUID.randomUUID(), category, "HIGH", null, title, evidence, "i", "r", null, "OBSERVED_PUBLIC_DATA");
    }

    private static AuditView audit(Instant at, String checks, String speed, FindingView... findings) {
        JsonNode c = JSON.readTree(checks);
        return new AuditView(UUID.randomUUID(), UUID.randomUUID(), "COMPLETED", "https://example.com", at, at, at,
                List.of(), JSON.readTree(speed), c, "x", "s", List.of(findings), List.of(), List.of(), null, null, null);
    }

    @Test
    void separatesFixedStillFailingAndNewProblems() {
        AuditView older = audit(Instant.parse("2026-11-01T10:00:00Z"),
                "[{\"key\":\"TITLE\",\"label\":\"Has a title\",\"passed\":false},"
                        + "{\"key\":\"H1\",\"label\":\"One h1\",\"passed\":false},"
                        + "{\"key\":\"HTTPS\",\"label\":\"HTTPS\",\"passed\":true},"
                        + "{\"key\":\"META\",\"label\":\"Meta\",\"passed\":null}]",
                "{\"available\":true,\"scores\":{\"performance\":70,\"seo\":80},\"metrics\":{\"lcp\":\"3.1 s\"}}",
                finding("SEO", "Title is a placeholder", "title: Example Domain"),
                finding("CONTENT", "No heading", "SINGLE_H1: FAIL"));
        AuditView newer = audit(Instant.parse("2026-11-08T10:00:00Z"),
                "[{\"key\":\"TITLE\",\"label\":\"Has a title\",\"passed\":true},"
                        + "{\"key\":\"H1\",\"label\":\"One h1\",\"passed\":false},"
                        + "{\"key\":\"HTTPS\",\"label\":\"HTTPS\",\"passed\":false},"
                        + "{\"key\":\"META\",\"label\":\"Meta\",\"passed\":true}]",
                "{\"available\":true,\"scores\":{\"performance\":85,\"seo\":80},\"metrics\":{\"lcp\":\"2.0 s\"}}",
                finding("CONTENT", "No main heading", "single_h1:  FAIL"),
                finding("SEO", "Meta description is short", "description: Hi"));

        AuditComparison c = AuditComparison.build(older, newer);

        assertEquals("FIXED", c.checks().get(0).change());
        assertEquals("STILL_FAILING", c.checks().get(1).change());
        assertEquals("NEWLY_FAILING", c.checks().get(2).change());
        assertEquals("NOT_ASSESSED", c.checks().get(3).change());
        assertEquals(1, c.findings().resolved().size());
        assertEquals("Title is a placeholder", c.findings().resolved().get(0).title());
        assertEquals(1, c.findings().persisting().size());
        assertEquals(1, c.findings().added().size());
        assertEquals(15, c.pageSpeedScores().get(0).change());
        assertEquals(0, c.pageSpeedScores().get(1).change());
        assertEquals("3.1 s", c.pageSpeedMetrics().get(0).before());
        assertEquals(1, c.totals().fixedChecks());
        assertTrue(c.note().contains("saved before"));
    }

    @Test
    void aFindingTheModelDidNotRepeatIsNotCalledResolvedWhileItsEvidenceIsStillThere() {
        FindingView old = finding("SEO", "Title is a placeholder", "title: Example Domain");
        AuditView older = audit(Instant.parse("2026-11-01T10:00:00Z"), "[]", "{\"available\":false}", old);
        AuditView newer = audit(Instant.parse("2026-11-08T10:00:00Z"), "[]", "{\"available\":false}");

        AuditComparison same = AuditComparison.build(older, newer, "page 1 title: Example Domain", "page 1 title: Example Domain");
        assertEquals(0, same.findings().resolved().size());
        assertEquals(1, same.findings().persisting().size());
        assertTrue(same.evidenceChecked());

        AuditComparison changed = AuditComparison.build(older, newer, "page 1 title: Example Domain", "page 1 title: Bare Botanics");
        assertEquals(1, changed.findings().resolved().size());

        AuditComparison unchecked = AuditComparison.build(older, newer, null, null);
        assertEquals(1, unchecked.findings().resolved().size());
        assertTrue(unchecked.note().contains("saved before"));
    }
}
