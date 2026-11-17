package com.launchmate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.content.ContentEvidence.ChannelContext;
import com.launchmate.content.ContentEvidence.Context;
import com.launchmate.content.ContentEvidence.Evidence;
import com.launchmate.content.ContentEvidence.RecommendationContext;
import com.launchmate.content.ContentEvidence.Request;
import com.launchmate.content.ContentEvidence.Site;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentEvidenceAndSpecsTest {

    private static final Instant WHEN = Instant.parse("2026-11-14T10:00:00Z");

    private static Request request(String type, String topic, String product) {
        return new Request(type, ContentSpecs.of(type).orElseThrow().label(), topic, "calm", product,
                "https://example.com/", 2);
    }

    @Test
    void everyTypeHasASpecWithRequiredPartsAndSaneLimits() {
        for (String type : List.of("HOMEPAGE_COPY", "META_TAGS", "PRODUCT_DESCRIPTION", "YT_TITLE", "YT_DESCRIPTION",
                "YT_HOOK", "YT_SCRIPT")) {
            ContentSpecs.Spec s = ContentSpecs.of(type).orElseThrow();
            assertEquals(type, s.type());
            assertTrue(s.minVariants() >= 1 && s.minVariants() <= s.defaultVariants()
                    && s.defaultVariants() <= s.maxVariants(), type);
            assertTrue(s.parts().stream().anyMatch(ContentSpecs.PartSpec::required), type);
            assertTrue(s.parts().stream().allMatch(p -> p.min() >= 1 && p.max() >= p.min()), type);
        }
        assertTrue(ContentSpecs.of("NOT_A_TYPE").isEmpty());
        assertTrue(ContentSpecs.of("YT_TITLE").orElseThrow().creatorOnly());
        assertFalse(ContentSpecs.of("META_TAGS").orElseThrow().creatorOnly());
        assertTrue(ContentSpecs.of("PRODUCT_DESCRIPTION").orElseThrow().needsProduct());
        assertTrue(ContentSpecs.of("YT_SCRIPT").orElseThrow().needsTopic());
    }

    @Test
    void instructionsAreGeneratedFromTheSpecSoTheyMatchTheValidator() {
        String text = ContentSpecs.instructions(ContentSpecs.of("META_TAGS").orElseThrow(), 2);
        assertTrue(text.contains("exactly 2 variant(s)"));
        assertTrue(text.contains("- title: 20 to 70 characters, required"));
        assertTrue(text.contains("- description: 60 to 170 characters, required"));
        String hook = ContentSpecs.instructions(ContentSpecs.of("YT_HOOK").orElseThrow(), 5);
        assertTrue(hook.contains("- visual_note: 5 to 200 characters, optional"));
    }

    @Test
    void evidenceHoldsTheRequestOwnerFactsAndCurrentText() {
        Evidence e = ContentEvidence.build(new Context(request("META_TAGS", null, null),
                List.of("name: Bare Botanics", "description: Plant-based skincare"), null, null, null,
                "title: Example Domain\ndescription: (none)"));
        String t = e.text();
        assertTrue(t.contains("CONTENT REQUEST: Meta title and description. Variants wanted: 2."));
        assertTrue(t.contains("- tone: calm"));
        assertTrue(t.contains("- page: https://example.com/"));
        assertTrue(t.contains("CURRENT TEXT (the owner's existing wording; improve on it and do not copy it):"));
        assertTrue(t.contains("  title: Example Domain"));
        assertTrue(t.contains("OWNER-PROVIDED INFORMATION (typed in by the owner; not verified):"));
        assertTrue(t.contains("- name: Bare Botanics"));
        assertFalse(t.contains("CURRENT WEBSITE"));
        assertFalse(t.contains("CHANNEL:"));
        assertTrue(e.existingTitles().isEmpty());
    }

    @Test
    void evidenceDescribesTheSiteTheChannelAndTheLinkedRecommendation() {
        Evidence e = ContentEvidence.build(new Context(request("YT_TITLE", "Packing for Manali", null),
                List.of("niche: Budget travel"),
                new Site("https://example.com", WHEN, "Example Domain", "", List.of("Example Domain"),
                        List.of("No main heading")),
                new ChannelContext("Aarav Creates (@aarav)", List.of("Goa in 3 days", "Budget tips"),
                        List.of("title style: average 21 characters"), List.of("Trips"), List.of("Goa in 3 days")),
                new RecommendationContext("Fix video descriptions", "Add a short description.", "empty descriptions: 3 of 8"),
                null));
        String t = e.text();
        assertTrue(t.contains("- topic: Packing for Manali"));
        assertTrue(t.contains("CURRENT WEBSITE (audited 2026-11-14; https://example.com):"));
        assertTrue(t.contains("- homepage meta description: (none)"));
        assertTrue(t.contains("- problems the audit found: No main heading"));
        assertTrue(t.contains("CHANNEL: Aarav Creates (@aarav)"));
        assertTrue(t.contains("  - Goa in 3 days"));
        assertTrue(t.contains("- recurring themes: Trips"));
        assertTrue(t.contains("LINKED RECOMMENDATION (the content should carry this out):"));
        assertTrue(t.contains("- evidence: empty descriptions: 3 of 8"));
        assertEquals(Set.of("Goa in 3 days", "Budget tips"), e.existingTitles());
    }

    @Test
    void theEvidenceIsTheGuardTextSoOwnerNumbersAreAcceptedAndOthersAreNot() {
        Evidence e = ContentEvidence.build(new Context(request("PRODUCT_DESCRIPTION", null, "Face cleanser"),
                List.of("description: made in small batches since 2021, price 24.99 USD"), null, null, null, null));
        assertTrue(ContentGuards.unsupportedNumbers("Since 2021 for 24.99 USD", e.text()).isEmpty());
        assertEquals(List.of("99%"), ContentGuards.unsupportedNumbers("99% natural", e.text()));
    }
}
