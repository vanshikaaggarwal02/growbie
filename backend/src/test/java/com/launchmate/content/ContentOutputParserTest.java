package com.launchmate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.content.ContentOutputParser.Output;
import com.launchmate.content.ContentSpecs.Spec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ContentOutputParserTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private static final String GUARD = "description: Plant-based skincare for sensitive skin, made in small batches. "
            + "Price 24.99 USD. Founded in 2021.";

    private static Spec spec(String type) {
        return ContentSpecs.of(type).orElseThrow();
    }

    private static Map<String, Object> variant(String label, String reason, String... nameText) {
        List<Map<String, String>> parts = new ArrayList<>();
        for (int i = 0; i < nameText.length; i += 2) {
            parts.add(Map.of("name", nameText[i], "text", nameText[i + 1]));
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("label", label);
        v.put("parts", parts);
        v.put("reason", reason);
        return v;
    }

    private String reply(List<Map<String, Object>> variants, String... notes) {
        return json.writeValueAsString(Map.of("variants", variants, "notes", List.of(notes)));
    }

    private static Map<String, Object> metaVariant(String label, String title, String description) {
        return variant(label, "Names the product and the audience.", "title", title, "description", description);
    }

    private Output parseMeta(String text, boolean strict) {
        return ContentOutputParser.parse(json, text, spec("META_TAGS"), GUARD, Set.of(), strict);
    }

    @Test
    void acceptsValidContentAndReturnsPartsInTheSpecsOrder() {
        String text = reply(List.of(variant("Benefit-led", "Names the product.",
                "description", "Plant-based skincare made in small batches for sensitive skin, from Bare Botanics.",
                "title", "Bare Botanics | Plant-Based Skincare for Sensitive Skin")), "Add your ingredient list.");
        Output out = parseMeta(text, true);
        assertEquals(1, out.variants().size());
        assertEquals("title", out.variants().get(0).parts().get(0).name(), "spec order, not the model's order");
        assertEquals("description", out.variants().get(0).parts().get(1).name());
        assertEquals(List.of("Add your ingredient list."), out.notes());
        assertEquals(0, out.dropped());
    }

    @Test
    void rejectsTextOutsideTheLengthLimits() {
        String longTitle = "A".repeat(71);
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(reply(List.of(metaVariant("x", longTitle,
                "Plant-based skincare made in small batches for sensitive skin."))), false));
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(reply(List.of(metaVariant("x",
                "Bare Botanics | Skincare", "Too short."))), false));
    }

    @Test
    void rejectsUnknownMissingAndRepeatedParts() {
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(reply(List.of(variant("x", "r",
                "title", "Bare Botanics | Plant-Based Skincare", "slogan", "A made-up part that is not in the spec"))), false));
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(reply(List.of(variant("x", "r",
                "title", "Bare Botanics | Plant-Based Skincare"))), false), "description is required");
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(reply(List.of(variant("x", "r",
                "title", "Bare Botanics | Plant-Based Skincare", "title", "Bare Botanics | Plant-Based Skincare Again",
                "description", "Plant-based skincare made in small batches for sensitive skin."))), false));
    }

    @Test
    void enforcesTheVariantCountLimitsOfEachType() {
        List<Map<String, Object>> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(variant("v" + i, "r", "title", "Budget travel title number " + i));
        }
        assertThrows(InvalidAiOutputException.class, () -> ContentOutputParser.parse(json, reply(four),
                spec("YT_TITLE"), GUARD, Set.of(), false), "YT_TITLE needs at least five variants");
        List<Map<String, Object>> five = new ArrayList<>(four);
        five.add(variant("v4", "r", "title", "Budget travel title number 4"));
        assertEquals(5, ContentOutputParser.parse(json, reply(five), spec("YT_TITLE"), GUARD, Set.of(), true)
                .variants().size());
    }

    @Test
    void strictModeRejectsInventedNumbersAndSaysWhich() {
        String text = reply(List.of(metaVariant("Offer", "Bare Botanics | Skincare, 30% off today",
                "Plant-based skincare made in small batches for sensitive skin, loved by 5,000 customers.")));
        InvalidAiOutputException e = assertThrows(InvalidAiOutputException.class, () -> parseMeta(text, true));
        assertTrue(e.getMessage().contains("30%") && e.getMessage().contains("5,000"), e.getMessage());
        assertTrue(e.getMessage().contains("never provided"), e.getMessage());
    }

    @Test
    void numbersTheOwnerSuppliedAreAccepted() {
        String text = reply(List.of(metaVariant("Facts", "Bare Botanics | Skincare since 2021",
                "Plant-based skincare for sensitive skin, made in small batches. Cleanser from 24.99 USD.")));
        assertEquals(0, parseMeta(text, true).dropped());
    }

    @Test
    void riskyClaimsAreRefusedUnlessTheOwnerMadeThem() {
        String text = reply(List.of(metaVariant("Claim", "Bare Botanics | Clinically Proven Skincare",
                "Guaranteed results for sensitive skin from plant-based skincare made in small batches.")));
        InvalidAiOutputException e = assertThrows(InvalidAiOutputException.class, () -> parseMeta(text, true));
        assertTrue(e.getMessage().contains("clinically"), e.getMessage());

        Output allowed = ContentOutputParser.parse(json, text, spec("META_TAGS"),
                GUARD + " Our formula is clinically proven and guaranteed.", Set.of(), true);
        assertEquals(1, allowed.variants().size());
    }

    @Test
    void lenientModeDropsOnlyTheOffendingVariantsAndCountsThem() {
        String text = reply(List.of(
                metaVariant("Good", "Bare Botanics | Plant-Based Skincare", "Plant-based skincare for sensitive skin, made in small batches."),
                metaVariant("Invented", "Bare Botanics | 50% off sale", "Plant-based skincare for sensitive skin, made in small batches."),
                metaVariant("Also good", "Bare Botanics | Gentle Everyday Skincare", "Gentle plant-based skincare for sensitive skin, made in small batches.")));
        Output out = parseMeta(text, false);
        assertEquals(2, out.variants().size());
        assertEquals(1, out.dropped());
        assertEquals("Good", out.variants().get(0).label());

        String allBad = reply(List.of(metaVariant("Invented", "Bare Botanics | 50% off sale",
                "Plant-based skincare for sensitive skin, made in small batches.")));
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(allBad, false), "nothing usable remained");
    }

    @Test
    void newYouTubeTitlesMustNotRepeatExistingOnesOrEachOther() {
        List<Map<String, Object>> six = new ArrayList<>();
        six.add(variant("a", "r", "title", "How I travel India on a student budget"));
        for (int i = 1; i < 5; i++) {
            six.add(variant("v" + i, "r", "title", "A brand new travel idea number " + i));
        }
        six.add(variant("dup", "r", "title", "A brand new travel idea number 1"));
        Set<String> existing = Set.of("how i travel india on a student budget");

        assertThrows(InvalidAiOutputException.class, () -> ContentOutputParser.parse(json, reply(six),
                spec("YT_TITLE"), GUARD, existing, true), "the first variant repeats an existing title");
        Output out = ContentOutputParser.parse(json, reply(six), spec("YT_TITLE"), GUARD, existing, false);
        assertEquals(4, out.variants().size(), "the existing-title variant is dropped and the repeat removed");
        assertEquals(1, out.dropped());
    }

    @Test
    void requiredHomepagePartsAreEnforcedAndOptionalOnesAreAllowed() {
        Map<String, Object> full = variant("Audience-led", "Speaks to the audience.",
                "headline", "Plant-based skincare for sensitive skin",
                "subheadline", "Made in small batches for adults whose skin reacts to everything.",
                "call_to_action", "Shop the range",
                "value_point_1", "Plant-based formulas for sensitive skin",
                "value_point_2", "Made in small batches, not mass produced",
                "value_point_3", "A face cleanser and a moisturiser to start",
                "about", "Bare Botanics makes plant-based skincare for sensitive skin in small batches.");
        assertEquals(1, ContentOutputParser.parse(json, reply(List.of(full)), spec("HOMEPAGE_COPY"), GUARD, Set.of(), true)
                .variants().size());

        Map<String, Object> missing = variant("x", "r", "headline", "Plant-based skincare for sensitive skin",
                "subheadline", "Made in small batches for adults whose skin reacts to everything.",
                "call_to_action", "Shop the range");
        assertThrows(InvalidAiOutputException.class, () -> ContentOutputParser.parse(json, reply(List.of(missing)),
                spec("HOMEPAGE_COPY"), GUARD, Set.of(), false), "the three value points are required");
    }

    @Test
    void structuralProblemsAlwaysFail() {
        assertThrows(InvalidAiOutputException.class, () -> parseMeta("not json", false));
        assertThrows(InvalidAiOutputException.class, () -> parseMeta("{\"notes\":[]}", false));
        assertThrows(InvalidAiOutputException.class, () -> parseMeta(reply(List.of()), false), "no variants");
    }
}
