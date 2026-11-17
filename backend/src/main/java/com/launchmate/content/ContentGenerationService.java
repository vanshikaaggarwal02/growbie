package com.launchmate.content;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.common.ApiException;
import com.launchmate.content.ContentContextLoader.SiteContext;
import com.launchmate.content.ContentDtos.ContentView;
import com.launchmate.content.ContentDtos.GenerateRequest;
import com.launchmate.content.ContentDtos.UpdateContentRequest;
import com.launchmate.content.ContentEvidence.ChannelContext;
import com.launchmate.content.ContentEvidence.Context;
import com.launchmate.content.ContentEvidence.Evidence;
import com.launchmate.content.ContentEvidence.RecommendationContext;
import com.launchmate.content.ContentEvidence.Request;
import com.launchmate.content.ContentOutputParser.Output;
import com.launchmate.content.ContentSpecs.Spec;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileFacts;
import com.launchmate.profile.ProfileService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The content studio: on-demand copy, titles, hooks and scripts. Each request is written from the
 * owner's own facts plus their latest saved audit or channel analysis, checked for invented numbers
 * and unsupported claims, saved as a draft, and approved by the owner when they pick a variant.
 * Nothing is ever published for them.
 */
@Service
public class ContentGenerationService {

    private static final int GENERATIONS_PER_HOUR = 30;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProfileService profiles;
    private final ContentContextLoader loader;
    private final ClaudeService claude;

    public ContentGenerationService(JdbcTemplate jdbc, ObjectMapper json, ProfileService profiles,
                                    ContentContextLoader loader, ClaudeService claude) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.loader = loader;
        this.claude = claude;
    }

    public ContentView generate(UUID owner, UUID profileId, GenerateRequest req) {
        ProfileResponse profile = profiles.get(owner, profileId); // enforces ownership
        Spec spec = ContentSpecs.of(req.contentType()).orElseThrow(
                () -> invalid("Unknown content type."));
        boolean hasChannel = "YOUTUBE_CREATOR".equals(profile.profileType())
                || (profile.channels() != null && !profile.channels().isEmpty());
        if (spec.creatorOnly() && !hasChannel) {
            throw invalid(spec.label() + " needs a YouTube channel on the business. Add one in the business details.");
        }
        if (spec.needsTopic() && blank(req.topic())) {
            throw invalid("Describe the topic of the video.");
        }
        if (spec.needsProduct() && blank(req.productName())) {
            throw invalid("Name the product to describe.");
        }
        int variants = req.count() == null ? spec.defaultVariants() : req.count();
        if (variants < spec.minVariants() || variants > spec.maxVariants()) {
            throw invalid(spec.label() + " needs between " + spec.minVariants() + " and " + spec.maxVariants()
                    + " variants.");
        }

        Integer recent = jdbc.queryForObject("""
                select count(*) from generated_content c join growth_profile p on p.id = c.growth_profile_id
                where p.owner_id = ? and c.content_type <> 'OTHER' and c.created_at > now() - interval '1 hour'
                """, Integer.class, owner);
        if (recent != null && recent >= GENERATIONS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "You have generated a lot of content in the last hour. Please try again later.");
        }

        RecommendationContext recommendation = recommendation(profileId, req.recommendationId());
        // YouTube content is written from the channel; everything else from the website audit, if any.
        boolean youtube = spec.type().startsWith("YT_");
        SiteContext site = youtube ? null : loader.site(profileId);
        ChannelContext channel = youtube ? loader.channel(profileId) : null;

        String original = blank(req.originalText()) ? null : req.originalText().trim();
        if (original == null && "META_TAGS".equals(spec.type())) {
            original = ContentContextLoader.originalMeta(site, req.pageUrl());
        }

        Request request = new Request(spec.type(), spec.label(), req.topic(), req.tone(), req.productName(),
                req.pageUrl(), variants);
        Evidence evidence = ContentEvidence.build(new Context(request, ProfileFacts.lines(profile),
                site == null ? null : site.site(), channel, recommendation, original));

        Output output = write(profileId, spec, variants, evidence);
        UUID id = save(profileId, req.recommendationId(), spec, req, variants, original, output);
        return get(owner, id);
    }

    public ContentView get(UUID owner, UUID id) {
        List<ContentView> rows = query(" where c.id = ? and p.owner_id = ?", id, owner);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Content not found.");
        }
        return rows.get(0);
    }

    public List<ContentView> list(UUID owner, UUID profileId, String type, UUID recommendationId) {
        profiles.get(owner, profileId);
        StringBuilder where = new StringBuilder(" where c.growth_profile_id = ? and p.owner_id = ? and c.content_type <> 'OTHER'");
        List<Object> args = new ArrayList<>(List.of(profileId, owner));
        if (type != null && !type.isBlank()) {
            where.append(" and c.content_type = ?");
            args.add(type);
        }
        if (recommendationId != null) {
            where.append(" and c.recommendation_id = ?");
            args.add(recommendationId);
        }
        return query(where + " order by c.created_at desc limit 50", args.toArray());
    }

    /** Approving records which variant the owner chose; returning to DRAFT clears the choice. */
    public ContentView update(UUID owner, UUID id, UpdateContentRequest request) {
        ContentView current = get(owner, id);
        Integer chosen = null;
        if ("APPROVED".equals(request.status())) {
            int count = current.variants().size();
            if (request.selectedVariant() == null || request.selectedVariant() > count) {
                throw invalid("Choose which variant to approve, from 1 to " + count + ".");
            }
            chosen = request.selectedVariant();
        }
        jdbc.update("update generated_content set status = ?, selected_variant = ?, updated_at = now() where id = ?",
                request.status(), chosen, id);
        return get(owner, id);
    }

    // ---- internals --------------------------------------------------------------------------

    private Output write(UUID profileId, Spec spec, int variants, Evidence evidence) {
        String user = "Create the content described in the EVIDENCE below. Follow the OUTPUT FORMAT exactly.\n\n"
                + ContentSpecs.instructions(spec, variants) + "\nEVIDENCE:\n" + evidence.text();
        ClaudeRequest request = new ClaudeRequest("content_" + spec.type().toLowerCase(), profileId,
                List.of("common_rules", "content_generation"), user, Model.CLAUDE_SONNET_5_5, 8000,
                OutputConfig.Effort.MEDIUM, AiSchemas.format(AiSchemas.CONTENT));
        // First attempt is strict, so the model hears exactly which number or claim was refused.
        AtomicInteger attempt = new AtomicInteger();
        try {
            return claude.completeValidated(request, text -> ContentOutputParser.parse(json, text, spec, evidence.text(),
                    evidence.existingTitles(), attempt.incrementAndGet() == 1));
        } catch (ApiException e) {
            if (!"CLAUDE_INVALID_OUTPUT".equals(e.getCode())) {
                throw e;
            }
            // Say why, in terms the owner can act on, instead of a generic failure.
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_NOT_SUPPORTED",
                    "This content could not be written using only the facts you have provided. Remove any claims, "
                            + "discounts, ratings or guarantees from your brief that you cannot back up, or add the "
                            + "supporting facts to your profile, then try again.");
        }
    }

    private RecommendationContext recommendation(UUID profileId, UUID recommendationId) {
        if (recommendationId == null) {
            return null;
        }
        List<RecommendationContext> rows = jdbc.query("""
                select r.title, r.suggested_action, r.evidence from recommendation r
                join growth_plan g on g.id = r.plan_id where r.id = ? and g.growth_profile_id = ?
                """, (rs, n) -> new RecommendationContext(rs.getString(1), rs.getString(2), rs.getString(3)),
                recommendationId, profileId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "Recommendation not found.");
        }
        return rows.get(0);
    }

    private UUID save(UUID profileId, UUID recommendationId, Spec spec, GenerateRequest req, int variants,
                      String original, Output output) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("variants", output.variants());
        body.put("notes", output.notes());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contentType", spec.type());
        request.put("topic", req.topic());
        request.put("tone", req.tone());
        request.put("productName", req.productName());
        request.put("pageUrl", req.pageUrl());
        request.put("variantsRequested", variants);
        request.put("variantsDropped", output.dropped());
        return jdbc.queryForObject("""
                insert into generated_content (growth_profile_id, recommendation_id, content_type, title, body,
                    original_text, metadata)
                values (?, ?, ?, ?, ?, ?, ?::jsonb) returning id
                """, UUID.class, profileId, recommendationId, spec.type(), spec.label(), json.writeValueAsString(body),
                original, json.writeValueAsString(Map.of("request", request)));
    }

    private List<ContentView> query(String whereAndOrder, Object... args) {
        return jdbc.query("""
                select c.id, c.growth_profile_id, c.recommendation_id, c.content_type, c.title, c.status,
                       c.selected_variant, c.original_text, c.body, c.metadata::text as metadata,
                       c.created_at, c.updated_at
                from generated_content c join growth_profile p on p.id = c.growth_profile_id
                """ + whereAndOrder, (rs, n) -> {
            JsonNode body = json.readTree(rs.getString("body"));
            List<String> notes = new ArrayList<>();
            body.path("notes").forEach(x -> notes.add(x.asString()));
            String metadata = rs.getString("metadata");
            return new ContentView(rs.getObject("id", UUID.class), rs.getObject("growth_profile_id", UUID.class),
                    rs.getObject("recommendation_id", UUID.class), rs.getString("content_type"), rs.getString("title"),
                    rs.getString("status"), (Integer) rs.getObject("selected_variant"), rs.getString("original_text"),
                    body.path("variants"), notes,
                    metadata == null ? null : json.readTree(metadata).path("request"),
                    instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
        }, args);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
