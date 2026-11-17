package com.launchmate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DomainNormaliserTest {

    @Test
    void stripsSchemeWwwPathAndCase() {
        assertEquals(Optional.of("barebotanics.com"),
                DomainNormaliser.normalise("HTTPS://www.BareBotanics.com/shop?x=1#top"));
    }

    @Test
    void keepsMultiPartTlds() {
        assertEquals(Optional.of("barebotanics.co.in"), DomainNormaliser.normalise("barebotanics.co.in"));
    }

    @Test
    void convertsInternationalNamesToPunycode() {
        assertEquals(Optional.of("xn--bcher-kva.de"), DomainNormaliser.normalise("bücher.de"));
    }

    @Test
    void rejectsInvalidInput() {
        for (String bad : new String[] {
                null, "", "   ", "barebotanics", "bad_domain.com", "-bad.com", "bad-.com", "a..com",
                "exa mple.com", "user@example.com", "example.com:8080", "example.c", "example.123",
                "a".repeat(64) + ".com"}) {
            assertTrue(DomainNormaliser.normalise(bad).isEmpty(), "should reject: " + bad);
        }
    }
}
