package com.launchmate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentGuardsTest {

    private static final String GUARD = "description: Plant-based skincare, made in small batches. Price 24.99 USD. "
            + "Founded in 2021 by two friends. 100% vegan formula. Ships in 3 to 5 days.";

    @Test
    void smallCountsAndCommonVideoLengthsAreAllowed() {
        assertTrue(ContentGuards.unsupportedNumbers("5 tips for sensitive skin in 60 seconds", "").isEmpty());
        assertTrue(ContentGuards.unsupportedNumbers("3 days, 10 minutes, 45 second demo, 90 seconds", "").isEmpty());
        assertTrue(ContentGuards.unsupportedNumbers("Part 2 of 7", "").isEmpty());
    }

    @Test
    void numbersTheOwnerGaveAreAllowedInAnyFormat() {
        assertTrue(ContentGuards.unsupportedNumbers("Only $24.99 a bottle", GUARD).isEmpty(), "price from the owner");
        assertTrue(ContentGuards.unsupportedNumbers("Since 2021", GUARD).isEmpty());
        assertTrue(ContentGuards.unsupportedNumbers("A 100% vegan formula", GUARD).isEmpty());
        assertTrue(ContentGuards.unsupportedNumbers("Ships in 3 to 5 days", GUARD).isEmpty());
        assertTrue(ContentGuards.unsupportedNumbers("Over 5,000 trips", "we have done 5000 trips").isEmpty(),
                "thousands separators are ignored");
    }

    @Test
    void inventedPercentagesPricesDecimalsAndLargeNumbersAreCaught() {
        assertEquals(List.of("30%"), ContentGuards.unsupportedNumbers("Save 30% today", GUARD));
        assertEquals(List.of("$19.99"), ContentGuards.unsupportedNumbers("Now only $19.99", GUARD));
        assertEquals(List.of("10,000"), ContentGuards.unsupportedNumbers("Loved by 10,000 customers", GUARD));
        assertEquals(List.of("4.8"), ContentGuards.unsupportedNumbers("Rated 4.8 out of 5", GUARD));
        assertEquals(List.of("2015"), ContentGuards.unsupportedNumbers("Trusted since 2015", GUARD));
    }

    @Test
    void aPercentageIsNotExcusedByACommonDurationOrASmallNumberInTheGuardText() {
        // 30 and 60 are common video lengths, but 30% and 60% are claims and need their own support.
        assertEquals(List.of("30%"), ContentGuards.unsupportedNumbers("30% off", "a 30 second demo"));
        assertEquals(List.of("60%"), ContentGuards.unsupportedNumbers("60% faster", ""));
        assertEquals(List.of("₹500"), ContentGuards.unsupportedNumbers("Only ₹500", "price 5 dollars"));
    }

    @Test
    void eachOffendingNumberIsReportedOnce() {
        assertEquals(List.of("30%"), ContentGuards.unsupportedNumbers("30% off, yes 30% off", ""));
    }

    @Test
    void riskyClaimsAreCaughtUnlessTheOwnerUsedThem() {
        assertEquals(List.of("guaranteed", "clinically", "proven"),
                ContentGuards.unsupportedClaims("Guaranteed results, clinically proven", "plant-based skincare"));
        assertEquals(List.of("award-winning"), ContentGuards.unsupportedClaims("Our award-winning cleanser", ""));
        assertEquals(List.of("dermatologist"), ContentGuards.unsupportedClaims("Dermatologist loved", ""));
        assertEquals(List.of("cure"), ContentGuards.unsupportedClaims("A cure for dry skin", ""));
        assertEquals(List.of("#1"), ContentGuards.unsupportedClaims("The #1 cleanser", ""));
        assertTrue(ContentGuards.unsupportedClaims("Dermatologist tested", "description: dermatologist tested").isEmpty(),
                "the owner said it first");
    }

    @Test
    void ordinaryWordsThatContainARiskyWordAreNotFlagged() {
        assertTrue(ContentGuards.unsupportedClaims("Secure checkout, a procure-to-pay guide, curved bottle",
                "").isEmpty(), "'secure' contains 'cure' but is a different word");
        assertTrue(ContentGuards.unsupportedClaims("Improved formula, approved packaging", "").isEmpty());
        assertTrue(ContentGuards.unsupportedClaims("Plant-based skincare for sensitive skin", "").isEmpty());
    }
}
