package com.launchmate.contact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ContactTest {

    @Test
    void limitsEachKeyWithinTheWindowThenLetsThemBackIn() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(2, Duration.ofHours(1), now::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "third attempt in the hour is refused");
        assertTrue(limiter.tryAcquire("b"), "another visitor is unaffected");
        now.set(Duration.ofHours(1).toMillis() + 1);
        assertTrue(limiter.tryAcquire("a"), "allowed again once the window has passed");
    }

    @Test
    void lineBreaksCannotInjectMailHeaders() {
        assertEquals("Eve Bcc: x@evil.test", ContactService.oneLine("Eve\r\nBcc: x@evil.test"));
    }
}
