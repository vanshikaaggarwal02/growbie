package com.launchmate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.domain.DomainAnalyzer.BrandMatch;
import com.launchmate.domain.DomainAnalyzer.DomainProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainAnalyzerTest {

    @Test
    void exactBrandMatchHasNoFlags() {
        DomainProperties p = DomainAnalyzer.analyse("barebotanics.com", "Bare Botanics");
        assertEquals(BrandMatch.EXACT, p.brandMatch());
        assertEquals("barebotanics", p.label());
        assertEquals("com", p.tld());
        assertEquals(12, p.length());
        assertEquals(List.of(), p.flags());
    }

    @Test
    void longHyphenatedNameIsFlagged() {
        DomainProperties p = DomainAnalyzer.analyse("bare-botanics-skin-care-online.com", "Bare Botanics");
        assertEquals(BrandMatch.CONTAINS_BRAND, p.brandMatch());
        assertEquals(4, p.hyphens());
        assertTrue(p.flags().contains("HAS_HYPHEN"));
        assertTrue(p.flags().contains("LONG_NAME"));
    }

    @Test
    void unrelatedNameWithDigitsIsFlagged() {
        DomainProperties p = DomainAnalyzer.analyse("xyz123.shop", "Bare Botanics");
        assertEquals(BrandMatch.DIFFERENT, p.brandMatch());
        assertEquals(3, p.digits());
        assertTrue(p.flags().contains("HAS_DIGITS"));
        assertTrue(p.flags().contains("BRAND_MISMATCH"));
        assertTrue(!p.flags().contains("UNCOMMON_TLD"));
    }

    @Test
    void closeSpellingIsSimilarAndUncommonTldIsFlagged() {
        DomainProperties p = DomainAnalyzer.analyse("barebotanix.xyz", "Bare Botanics");
        assertEquals(BrandMatch.SIMILAR, p.brandMatch());
        assertTrue(p.flags().contains("UNCOMMON_TLD"));
    }

    @Test
    void missingBrandIsReportedNotGuessed() {
        assertEquals(BrandMatch.NOT_PROVIDED, DomainAnalyzer.analyse("example.com", " ").brandMatch());
        assertEquals(BrandMatch.NOT_PROVIDED, DomainAnalyzer.analyse("example.com", null).brandMatch());
    }
}
