package com.launchmate.ai;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Writes one row per Claude call to ai_usage so token spend and cache hits are visible. */
@Service
public class UsageRecorder {

    private final JdbcTemplate jdbc;

    public UsageRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID profileId, String feature, String model, String promptVersion,
                       long inputTokens, long outputTokens, long cacheCreationTokens,
                       long cacheReadTokens, String stopReason, long latencyMs, String requestId) {
        jdbc.update("""
                INSERT INTO ai_usage (growth_profile_id, feature, model, prompt_version,
                    input_tokens, output_tokens, cache_creation_input_tokens,
                    cache_read_input_tokens, stop_reason, latency_ms, request_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                profileId, feature, model, promptVersion,
                (int) inputTokens, (int) outputTokens, (int) cacheCreationTokens,
                (int) cacheReadTokens, stopReason, (int) latencyMs, requestId);
    }
}
