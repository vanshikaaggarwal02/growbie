package com.launchmate.channel;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiJson;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileFacts;
import com.launchmate.profile.ProfileService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Whether YouTube suits a business that has no channel. It is an on-demand judgement from the owner's own
 * answers (clearly AI-derived, never measured), and a result from the last seven days is reused so the
 * same question is not paid for twice.
 */
@Service
public class YouTubeRelevanceService {

    private static final Set<String> CHOICES = Set.of("START_NOW", "CONSIDER_LATER", "FOCUS_ELSEWHERE_FIRST");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProfileService profiles;
    private final ClaudeService claude;

    public YouTubeRelevanceService(JdbcTemplate jdbc, ObjectMapper json, ProfileService profiles, ClaudeService claude) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.claude = claude;
    }

    /** The latest saved assessment, or null if none has been made. */
    public JsonNode latest(UUID owner, UUID profileId) {
        profiles.get(owner, profileId); // enforces ownership
        List<String> rows = jdbc.query("""
                select body from generated_content
                where growth_profile_id = ? and metadata->>'kind' = 'YOUTUBE_RELEVANCE'
                order by created_at desc limit 1
                """, (rs, n) -> rs.getString(1), profileId);
        return rows.isEmpty() ? null : json.readTree(rows.get(0));
    }

    public JsonNode assess(UUID owner, UUID profileId) {
        ProfileResponse profile = profiles.get(owner, profileId);
        List<String> recent = jdbc.query("""
                select body from generated_content
                where growth_profile_id = ? and metadata->>'kind' = 'YOUTUBE_RELEVANCE'
                  and created_at > now() - interval '7 days'
                order by created_at desc limit 1
                """, (rs, n) -> rs.getString(1), profileId);
        if (!recent.isEmpty()) {
            return json.readTree(recent.get(0));
        }
        List<String> facts = ProfileFacts.lines(profile);
        ClaudeRequest request = new ClaudeRequest("youtube_relevance", profileId,
                List.of("common_rules", "youtube_relevance"),
                "Decide whether YouTube suits this business.\n\nBUSINESS FACTS:\n- " + String.join("\n- ", facts),
                Model.CLAUDE_SONNET_5_5, 2000, OutputConfig.Effort.LOW, AiSchemas.format(AiSchemas.YOUTUBE_RELEVANCE));
        JsonNode result = claude.completeValidated(request, text -> {
            JsonNode root = AiJson.parseObject(json, text);
            AiJson.oneOf(root, "recommendation", CHOICES);
            AiJson.str(root, "summary", 1, 500);
            AiJson.strings(root, "reasons", 1, 6, 400);
            AiJson.strings(root, "firstSteps", 0, 5, 300);
            AiJson.strings(root, "limitations", 0, 5, 400);
            return root;
        });
        jdbc.update("""
                insert into generated_content (growth_profile_id, content_type, title, body, metadata)
                values (?, 'OTHER', 'YouTube relevance', ?, '{"kind":"YOUTUBE_RELEVANCE"}'::jsonb)
                """, profileId, json.writeValueAsString(result));
        return result;
    }
}
