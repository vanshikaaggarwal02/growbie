package com.launchmate.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads "Authorization: Bearer <token>" and, if the token is valid, authenticates the request. */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final Function<UUID, Instant> passwordChangedAt;

    public JwtAuthFilter(JwtService jwt) {
        this(jwt, id -> null);
    }

    /** {@code passwordChangedAt} returns when the user last changed their password, or null if never. */
    public JwtAuthFilter(JwtService jwt, Function<UUID, Instant> passwordChangedAt) {
        this.jwt = jwt;
        this.passwordChangedAt = passwordChangedAt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.verify(header.substring(7).trim()).ifPresent(v -> {
                // A session issued before the last password change is no longer valid.
                Instant changed = passwordChangedAt.apply(v.userId());
                if (changed != null && v.issuedAt().isBefore(changed.truncatedTo(ChronoUnit.SECONDS))) {
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(v.userId().toString(), null, List.of()));
            });
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
