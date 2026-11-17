package com.launchmate.auth;

import com.launchmate.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record UserDto(UUID id, String email, String displayName) {}

    public record AuthResponse(String token, UserDto user) {}

    private record UserRow(UUID id, String passwordHash, String displayName) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RESETS_PER_HOUR = 3;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PasswordResetMailer mailer;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwt, PasswordResetMailer mailer) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mailer = mailer;
    }

    @Transactional
    public AuthResponse register(String email, String password, String displayName) {
        String normalised = normalise(email);
        Integer existing = jdbc.queryForObject(
                "select count(*) from app_user where email = ?", Integer.class, normalised);
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                    "An account with this email already exists.");
        }
        String name = displayName.trim();
        UUID id = jdbc.queryForObject(
                "insert into app_user (email, password_hash, display_name) values (?, ?, ?) returning id",
                UUID.class, normalised, encoder.encode(password), name);
        return new AuthResponse(jwt.issue(id), new UserDto(id, normalised, name));
    }

    public AuthResponse login(String email, String password) {
        String normalised = normalise(email);
        List<UserRow> rows = jdbc.query(
                "select id, password_hash, display_name from app_user where email = ?",
                (rs, n) -> new UserRow(rs.getObject("id", UUID.class),
                        rs.getString("password_hash"), rs.getString("display_name")),
                normalised);
        // Same error for an unknown email and a wrong password, so accounts cannot be probed.
        if (rows.isEmpty() || !encoder.matches(password, rows.get(0).passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "The email or password is incorrect.");
        }
        UserRow user = rows.get(0);
        return new AuthResponse(jwt.issue(user.id()), new UserDto(user.id(), normalised, user.displayName()));
    }

    /**
     * Starts a password reset. It does the same thing from the outside whether or not the email has an
     * account, so the form cannot be used to find out who is registered.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        String normalised = normalise(email);
        List<UUID> ids = jdbc.query("select id from app_user where email = ?",
                (rs, n) -> rs.getObject("id", UUID.class), normalised);
        if (ids.isEmpty()) {
            return;
        }
        UUID userId = ids.get(0);
        Integer recent = jdbc.queryForObject("""
                select count(*) from password_reset_token
                where user_id = ? and created_at > now() - interval '1 hour'
                """, Integer.class, userId);
        if (recent != null && recent >= RESETS_PER_HOUR) {
            return; // quietly ignore a flood of requests for the same account
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        jdbc.update("update password_reset_token set used_at = now() where user_id = ? and used_at is null", userId);
        jdbc.update("""
                insert into password_reset_token (user_id, token_hash, expires_at)
                values (?, ?, now() + interval '30 minutes')
                """, userId, sha256(token));
        mailer.sendReset(normalised, token);
    }

    /** Sets a new password from a valid, unused, unexpired token. The token works once. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        List<UUID[]> rows = jdbc.query("""
                select id, user_id from password_reset_token
                where token_hash = ? and used_at is null and expires_at > now()
                """, (rs, n) -> new UUID[] {rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class)},
                sha256(token.trim()));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                    "This reset link is invalid or has expired. Please request a new one.");
        }
        UUID userId = rows.get(0)[1];
        jdbc.update("update app_user set password_hash = ?, password_changed_at = now() where id = ?",
                encoder.encode(newPassword), userId);
        jdbc.update("update password_reset_token set used_at = now() where user_id = ? and used_at is null", userId);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
