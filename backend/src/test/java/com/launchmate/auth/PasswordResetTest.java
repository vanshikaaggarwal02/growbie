package com.launchmate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class PasswordResetTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokensAreStoredOnlyAsAHashAndTheHashIsStable() {
        String a = AuthService.sha256("some-token");
        assertEquals(64, a.length());
        assertEquals(a, AuthService.sha256("some-token"));
        assertNotEquals(a, AuthService.sha256("another-token"));
        assertNotEquals("some-token", a);
    }

    private Authentication run(JwtAuthFilter filter, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        AtomicReference<Authentication> seen = new AtomicReference<>();
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        return seen.get();
    }

    @Test
    void aSessionIssuedBeforeAPasswordChangeIsRejected() throws Exception {
        JwtService jwt = new JwtService(SECRET, 24);
        UUID user = UUID.randomUUID();
        String token = jwt.issue(user);

        assertNotNull(run(new JwtAuthFilter(jwt, id -> null), token), "no password change: session works");
        assertNotNull(run(new JwtAuthFilter(jwt, id -> Instant.now().minusSeconds(3600)), token),
                "password changed before the session was issued: still works");
        assertNull(run(new JwtAuthFilter(jwt, id -> Instant.now().plusSeconds(60)), token),
                "password changed after the session was issued: rejected");
    }
}
