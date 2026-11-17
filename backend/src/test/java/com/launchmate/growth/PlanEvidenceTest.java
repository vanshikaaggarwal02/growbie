package com.launchmate.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.growth.PlanEvidence.Evidence;
import com.launchmate.growth.PlanInputs.Audit;
import com.launchmate.growth.PlanInputs.BrandName;
import com.launchmate.growth.PlanInputs.Channel;
import com.launchmate.growth.PlanInputs.DomainRow;
import com.launchmate.growth.PlanInputs.Finding;
import com.launchmate.growth.PlanInputs.Gap;
import com.launchmate.growth.PlanInputs.Progress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanEvidenceTest {

    private static final Instant WHEN = Instant.parse("2026-11-14T10:00:00Z");

    private static Audit audit() {
        return new Audit(UUID.randomUUID(), "https://example.com", WHEN, "10 of 14 checks passed (1 could not be assessed)",
                List.of("TITLE_LENGTH: FAIL - the title is 14 characters", "SINGLE_H1: FAIL - no h1 heading was found"),
                Map.of("performance", 100, "accessibility", 96, "bestPractices", 100, "seo", 80),
                List.of(new Finding("HIGH", "SEO", "No main heading", "SINGLE_H1: FAIL - no h1 heading was found",
                                "Add one h1."),
                        new Finding("MEDIUM", "CONTENT", "Page does not say what is offered", "navigation: (none)",
                                "Describe the offer.")));
    }

    private static Channel channel() {
        return new Channel(UUID.randomUUID(), WHEN, "Aarav Creates",
                List.of("uploads: about 1.0 per week; median gap 7.0 days", "titles: 3 of 8 contain a number"),
                List.of(new Gap("Empty descriptions", "empty descriptions: 3 of 8", "Add a short description.")));
    }

    private static PlanInputs inputs(String type, Audit a, Channel c, List<DomainRow> d, List<BrandName> b,
                                     List<Progress> p, List<String> launch) {
        return new PlanInputs(type, "Bare Botanics", List.of("goal: Get first online orders", "audience: Adults"),
                launch, a, c, d, b, p);
    }

    private static DomainRow domain(String name, String availability, String price, String premium, String match) {
        return new DomainRow(name, "USER_CANDIDATE", availability, price, premium, WHEN, match, List.of());
    }

    @Test
    void everyCitableLineCarriesAnIdAndTheIdsAreReturned() {
        Evidence e = PlanEvidence.build(inputs("EXISTING_BUSINESS", audit(), channel(),
                List.of(domain("barebotanics.in", "AVAILABLE", "12.99 USD", null, "EXACT"),
                        domain("barebotanics.com", "AVAILABLE", "22.99 USD", "2488.00 USD", "EXACT")),
                List.of(new BrandName("Softleaf", "Gentle skincare"), new BrandName("Verda", "Simple plant care")),
                List.of(), List.of()));

        assertEquals(Set.of("OWNER-1", "OWNER-2", "AUDIT-S", "AUDIT-C1", "AUDIT-C2", "AUDIT-PSI", "AUDIT-1", "AUDIT-2",
                "CHANNEL-S1", "CHANNEL-S2", "CHANNEL-G1", "DOMAIN-1", "DOMAIN-2", "BRAND-1", "BRAND-2"), e.ids());
        for (String id : e.ids()) {
            assertTrue(e.text().contains("[" + id + "]"), "the text must show [" + id + "]");
        }
    }

    @Test
    void writesAuditChannelDomainAndBrandLinesInQuotableForm() {
        String text = PlanEvidence.build(inputs("EXISTING_BUSINESS", audit(), channel(),
                List.of(domain("barebotanics.com", "AVAILABLE", "22.99 USD", "2488.00 USD", "EXACT")),
                List.of(new BrandName("Softleaf", "Gentle skincare")), List.of(), List.of())).text();
        for (String line : List.of(
                "[AUDIT-S] readiness: 10 of 14 checks passed (1 could not be assessed)",
                "[AUDIT-C2] SINGLE_H1: FAIL - no h1 heading was found",
                "[AUDIT-PSI] PageSpeed mobile scores: performance 100, accessibility 96, best practices 100, seo 80",
                "[AUDIT-1] HIGH SEO | No main heading | evidence: SINGLE_H1: FAIL - no h1 heading was found | fix: Add one h1.",
                "[CHANNEL-S1] uploads: about 1.0 per week; median gap 7.0 days",
                "[CHANNEL-G1] Empty descriptions | evidence: empty descriptions: 3 of 8 | suggestion: Add a short description.",
                "[DOMAIN-1] barebotanics.com (USER_CANDIDATE): available when checked on 2026-11-14, indicative price "
                        + "22.99 USD per year, premium one-time fee 2488.00 USD; brand match EXACT; flags: none",
                "[BRAND-1] Softleaf - Gentle skincare",
                "NOT AVAILABLE: sales, revenue, website traffic")) {
            assertTrue(text.contains(line), "evidence is missing: " + line + "\n---\n" + text);
        }
    }

    @Test
    void sayWhatIsUnknownAboutDomainsAndOmitSectionsThatHaveNoData() {
        String text = PlanEvidence.build(inputs("NEW_BUSINESS", null, null,
                List.of(domain("a.com", "UNKNOWN", null, null, "EXACT"), domain("b.com", "NOT_CHECKED", null, null, "SIMILAR"),
                        domain("c.com", "UNAVAILABLE", null, null, "EXACT")), List.of(), List.of(), List.of())).text();
        assertTrue(text.contains("availability could not be confirmed"));
        assertTrue(text.contains("availability not checked"));
        assertTrue(text.contains("not available when checked on 2026-11-14"));
        assertFalse(text.contains("WEBSITE AUDIT"));
        assertFalse(text.contains("CHANNEL ANALYSIS"));
        assertFalse(text.contains("BRAND NAME SUGGESTIONS"));
        assertFalse(text.contains("PROGRESS SO FAR"));
        assertFalse(text.contains("LAUNCH FACTS"));
    }

    @Test
    void launchFactsAndProgressAreIncludedWithTheirIds() {
        Evidence e = PlanEvidence.build(inputs("NEW_BUSINESS", null, null, List.of(), List.of(), List.of(
                new Progress("COMPLETED", "DOMAIN", "Secure barebotanics.in", "bought it"),
                new Progress("DISMISSED", "BRAND", "Rename the business", null)),
                List.of("website address: none given", "domain candidates checked: none yet")));

        assertTrue(e.text().contains("new business, not yet launched"));
        assertTrue(e.text().contains("[LAUNCH-1] website address: none given"));
        assertTrue(e.text().contains("[PROGRESS-1] COMPLETED | DOMAIN | Secure barebotanics.in | owner note: bought it"));
        assertTrue(e.text().contains("[PROGRESS-2] DISMISSED | BRAND | Rename the business"));
        assertFalse(e.text().contains("[PROGRESS-2] DISMISSED | BRAND | Rename the business | owner note"));
        assertTrue(e.ids().containsAll(Set.of("LAUNCH-1", "LAUNCH-2", "PROGRESS-1", "PROGRESS-2")));
    }

    @Test
    void hasAnalysisIsFalseWhenOnlyOwnerFactsExist() {
        assertFalse(inputs("NEW_BUSINESS", null, null, List.of(), List.of(), List.of(), List.of()).hasAnalysis());
        assertTrue(inputs("NEW_BUSINESS", audit(), null, List.of(), List.of(), List.of(), List.of()).hasAnalysis());
        assertTrue(inputs("NEW_BUSINESS", null, null, List.of(), List.of(new BrandName("A", "b")), List.of(), List.of())
                .hasAnalysis());
    }
}
