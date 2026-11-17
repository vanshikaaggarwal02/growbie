package com.launchmate.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and verifies signed (HS256) tokens. The subject is the user's id. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${launchmate.jwt.secret}") String secret,
                      @Value("${launchmate.jwt.ttl-hours:24}") long ttlHours) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String issue(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** A verified token's owner and the moment it was issued. */
    public record Verified(UUID userId, Instant issuedAt) {}

    /** Returns the user id for a valid, unexpired token, otherwise empty. */
    public Optional<UUID> parse(String token) {
        return verify(token).map(Verified::userId);
    }

    public Optional<Verified> verify(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Date issued = claims.getIssuedAt();
            return Optional.of(new Verified(UUID.fromString(claims.getSubject()),
                    issued == null ? Instant.EPOCH : issued.toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
