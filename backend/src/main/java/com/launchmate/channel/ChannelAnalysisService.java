package com.launchmate.channel;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.channel.ChannelAnalysisDtos.ChannelAnalysisView;
import com.launchmate.channel.ChannelAnalysisDtos.SnapshotItem;
import com.launchmate.channel.ChannelAnalysisDtos.StartAnalysisRequest;
import com.launchmate.channel.ChannelAnalysisDtos.StartAnalysisResponse;
import com.launchmate.channel.ChannelAnalysisDtos.VideoView;
import com.launchmate.channel.ChannelEvidence.Evidence;
import com.launchmate.channel.ChannelOutputParser.Output;
import com.launchmate.channel.ChannelStats.Stats;
import com.launchmate.channel.YouTubeData.ChannelInfo;
import com.launchmate.channel.YouTubeData.RecentVideos;
import com.launchmate.channel.YouTubeData.VideoInfo;
import com.launchmate.common.ApiException;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs YouTube channel analyses in the background. Each run saves a measured snapshot of the channel
 * (so growth can be compared over time), the videos it looked at, and, when the model succeeds, its
 * interpretation. If the model is unavailable the measured facts are still saved (PARTIAL).
 */
@Service
public class ChannelAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ChannelAnalysisService.class);
    private static final int DEFAULT_VIDEOS = 20;
    private static final int MIN_VIDEOS = 3;
    private static final int ANALYSES_PER_HOUR = 10;
    private static final List<String> FIXED_LIMITATIONS = List.of(
            "Watch time, audience retention, impressions, click-through rate, audience demographics, traffic "
                    + "sources and revenue are not available from public YouTube data.");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProfileService profiles;
    private final YouTubeClient youtube;
    private final ClaudeService claude;
    private final ThreadPoolExecutor executor;

    public ChannelAnalysisService(JdbcTemplate jdbc, ObjectMapper json, ProfileService profiles,
                                  YouTubeClient youtube, ClaudeService claude,
                                  @Qualifier("analysisExecutor") ThreadPoolExecutor executor) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.youtube = youtube;
        this.claude = claude;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedAnalyses() {
        int n = jdbc.update("""
                update channel_analysis set status = 'FAILED', completed_at = now(),
                    warnings = '["The analysis was interrupted by a server restart. Please run it again."]'::jsonb
                where status in ('QUEUED', 'RUNNING')
                """);
        if (n > 0) {
            log.warn("marked {} interrupted channel analysis(es) as failed", n);
        }
    }

    public StartAnalysisResponse start(UUID owner, UUID profileId, StartAnalysisRequest request) {
        ProfileResponse profile = profiles.get(owner, profileId); // enforces ownership
        record ChannelRow(UUID id, String url) {}
        List<ChannelRow> rows = jdbc.query("""
                select id, channel_url from creator_channel
                where growth_profile_id = ? and platform = 'YOUTUBE' order by created_at limit 1
                """, (rs, n) -> new ChannelRow(rs.getObject("id", UUID.class), rs.getString("channel_url")), profileId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Add a YouTube channel to this profile first.");
        }
        ChannelRow channelRow = rows.get(0);
        ChannelRef ref;
        try {
            ref = ChannelRef.parse(channelRow.url());
        } catch (ChannelRef.Unsupported e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHANNEL_URL", e.getMessage());
        }
        int maxVideos = request.maxVideos() == null ? DEFAULT_VIDEOS : request.maxVideos();

        Integer recent = jdbc.queryForObject("""
                select count(*) from channel_analysis a join growth_profile p on p.id = a.growth_profile_id
                where p.owner_id = ? and a.created_at > now() - interval '1 hour'
                """, Integer.class, owner);
        if (recent != null && recent >= ANALYSES_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "You have run several analyses in the last hour. Please try again later.");
        }
        Integer active = jdbc.queryForObject("""
                select count(*) from channel_analysis
                where growth_profile_id = ? and status in ('QUEUED', 'RUNNING')
                """, Integer.class, profileId);
        if (active != null && active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS",
                    "An analysis for this profile is already running. Wait for it to finish.");
        }

        UUID analysisId = jdbc.queryForObject("""
                insert into channel_analysis (growth_profile_id, channel_id, status) values (?, ?, 'QUEUED') returning id
                """, UUID.class, profileId, channelRow.id());
        List<String> facts = ownerFacts(profile);
        try {
            executor.execute(() -> run(analysisId, profileId, channelRow.id(), ref, maxVideos, facts));
        } catch (RejectedExecutionException e) {
            fail(analysisId, "The analysis queue is full. Please try again in a few minutes.", null);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "Many analyses are running right now. Please try again in a few minutes.");
        }
        return new StartAnalysisResponse(analysisId, "QUEUED");
    }

    public ChannelAnalysisView get(UUID owner, UUID analysisId) {
        record Row(UUID id, UUID profileId, String status, Instant created, Instant started, Instant completed,
                   String stats, String analysis, String limitations, String warnings) {}
        List<Row> rows = jdbc.query("""
                select a.id, a.growth_profile_id, a.status, a.created_at, a.started_at, a.completed_at,
                       a.channel_stats::text as stats, a.analysis::text as analysis,
                       a.data_limitations::text as limitations, a.warnings::text as warnings
                from channel_analysis a join growth_profile p on p.id = a.growth_profile_id
                where a.id = ? and p.owner_id = ?
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("growth_profile_id", UUID.class),
                rs.getString("status"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")), rs.getString("stats"), rs.getString("analysis"),
                rs.getString("limitations"), rs.getString("warnings")), analysisId, owner);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analysis not found.");
        }
        Row r = rows.get(0);

        List<VideoView> videos = jdbc.query("""
                select youtube_video_id, title, published_at, duration_seconds, format, views, likes, comments
                from channel_video where analysis_id = ? order by published_at desc
                """, (rs, n) -> new VideoView(rs.getString("youtube_video_id"), rs.getString("title"),
                instant(rs.getTimestamp("published_at")), (Integer) rs.getObject("duration_seconds"),
                rs.getString("format"), (Long) rs.getObject("views"), (Long) rs.getObject("likes"),
                (Long) rs.getObject("comments")), analysisId);

        JsonNode snapshot = r.stats() == null ? null : json.readTree(r.stats());
        return new ChannelAnalysisView(r.id(), r.profileId(), r.status(), r.created(), r.started(), r.completed(),
                snapshot == null ? null : snapshot.get("channel"), snapshot == null ? null : snapshot.get("stats"),
                r.analysis() == null ? null : json.readTree(r.analysis()), videos, strings(r.limitations()),
                strings(r.warnings()));
    }

    /** Measured snapshots, newest first, so growth between analyses can be seen. */
    public List<SnapshotItem> list(UUID owner, UUID profileId) {
        profiles.get(owner, profileId);
        return jdbc.query("""
                select id, status, created_at, channel_stats::text as stats from channel_analysis
                where growth_profile_id = ? order by created_at desc limit 30
                """, (rs, n) -> {
            String stats = rs.getString("stats");
            JsonNode s = stats == null ? null : json.readTree(stats);
            JsonNode ch = s == null ? null : s.get("channel");
            JsonNode cad = s == null ? null : s.path("stats").path("cadence");
            return new SnapshotItem(rs.getObject("id", UUID.class), rs.getString("status"),
                    instant(rs.getTimestamp("created_at")),
                    s == null || !s.hasNonNull("measuredAt") ? null : Instant.parse(s.get("measuredAt").asString()),
                    ch == null || !ch.hasNonNull("subscribers") ? null : ch.get("subscribers").asLong(),
                    ch != null && ch.path("subscribersHidden").asBoolean(false),
                    ch == null || !ch.hasNonNull("totalViews") ? null : ch.get("totalViews").asLong(),
                    ch == null || !ch.hasNonNull("videoCount") ? null : ch.get("videoCount").asLong(),
                    cad == null || !cad.hasNonNull("uploadsPerWeek") ? null : cad.get("uploadsPerWeek").asDouble(),
                    s == null || !s.path("stats").hasNonNull("videosAnalysed") ? null
                            : s.path("stats").get("videosAnalysed").asInt());
        }, profileId);
    }

    // ---- background work -------------------------------------------------------------------

    private static List<String> ownerFacts(ProfileResponse profile) {
        List<String> facts = new ArrayList<>();
        addFact(facts, "creator or channel name", profile.displayName());
        addFact(facts, "audience", profile.targetAudience());
        addFact(facts, "market", profile.marketLocation());
        addFact(facts, "goal", profile.primaryGoal());
        if (profile.creatorDetails() != null) {
            addFact(facts, "niche", profile.creatorDetails().niche());
            addFact(facts, "about the creator", profile.creatorDetails().creatorBio());
            addFact(facts, "content language", profile.creatorDetails().contentLanguage());
            JsonNode goals = profile.creatorDetails().contentGoals();
            if (goals != null && goals.isArray() && !goals.isEmpty()) {
                List<String> items = new ArrayList<>();
                goals.forEach(g -> items.add(g.asString()));
                addFact(facts, "content goals", String.join("; ", items));
            }
        }
        return List.copyOf(facts);
    }

    private static void addFact(List<String> facts, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(label + ": " + value.trim());
        }
    }

    private void run(UUID analysisId, UUID profileId, UUID channelRowId, ChannelRef ref, int maxVideos,
                     List<String> facts) {
        try {
            jdbc.update("update channel_analysis set status = 'RUNNING', started_at = now() where id = ?", analysisId);

            ChannelInfo channel;
            List<VideoInfo> videos;
            RecentVideos recent;
            try {
                channel = youtube.channel(ref);
                recent = youtube.recentVideos(channel, maxVideos);
                videos = recent.videos();
            } catch (ApiException e) {
                if ("YOUTUBE_CHANNEL_NOT_FOUND".equals(e.getCode())) {
                    jdbc.update("update creator_channel set connection_status = 'NOT_FOUND' where id = ?", channelRowId);
                }
                fail(analysisId, e.getMessage(), null);
                return;
            }
            jdbc.update("""
                    update creator_channel set platform_channel_id = ?, last_synced_at = now(),
                        connection_status = 'URL_ONLY' where id = ?
                    """, channel.channelId(), channelRowId);

            Instant now = Instant.now();
            Stats stats = ChannelStats.compute(videos, now, recent.formatsObserved(), maxVideos);
            String snapshot = json.writeValueAsString(snapshot(channel, recent, stats, now));
            if (stats.videosAnalysed() < MIN_VIDEOS) {
                fail(analysisId, "This channel needs at least " + MIN_VIDEOS
                        + " public videos before it can be analysed.", snapshot);
                return;
            }

            Evidence evidence = ChannelEvidence.build(channel, videos, stats, facts, now);
            List<String> warnings = new ArrayList<>();
            Output output = null;
            try {
                output = interpret(profileId, evidence);
            } catch (ApiException e) {
                warnings.add("The written analysis is unavailable right now (" + e.getMessage()
                        + ") The measured statistics below are still accurate.");
            }
            if (output != null && output.dropped() > 0) {
                warnings.add(output.dropped() + " item(s) were removed because they could not be verified against "
                        + "the channel's real data.");
            }
            save(analysisId, output == null ? "PARTIAL" : "COMPLETED", videos, stats, snapshot, output, warnings, maxVideos);
        } catch (Throwable t) {
            log.error("channel analysis {} failed unexpectedly: {}", analysisId, t.getClass().getSimpleName(), t);
            fail(analysisId, "The analysis stopped unexpectedly. Please try again.", null);
        }
    }

    private Output interpret(UUID profileId, Evidence evidence) {
        ClaudeRequest request = new ClaudeRequest("channel_analysis", profileId,
                List.of("common_rules", "channel_analysis"),
                "Analyse the YouTube channel described in the EVIDENCE below.\n\nEVIDENCE:\n" + evidence.text(),
                Model.CLAUDE_SONNET_5_5, 16000, OutputConfig.Effort.MEDIUM,
                AiSchemas.format(AiSchemas.CHANNEL_ANALYSIS));
        // First attempt is strict so the model is told exactly what failed verification; on the
        // retry unverifiable items are dropped and counted instead of shown.
        AtomicInteger attempt = new AtomicInteger();
        return claude.completeValidated(request, text -> ChannelOutputParser.parse(json, text,
                evidence.videoTitles(), evidence.text(), attempt.incrementAndGet() == 1));
    }

    private Map<String, Object> snapshot(ChannelInfo c, RecentVideos recent, Stats stats, Instant now) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("channelId", c.channelId());
        channel.put("title", c.title());
        channel.put("handle", c.handle());
        channel.put("country", c.country());
        channel.put("createdAt", c.publishedAt() == null ? null : c.publishedAt().toString());
        channel.put("subscribers", c.subscribers());
        channel.put("subscribersHidden", c.subscribersHidden());
        channel.put("totalViews", c.totalViews());
        channel.put("videoCount", c.videoCount());
        channel.put("shortsCount", recent.shortsTotal());
        channel.put("longFormCount", recent.longFormTotal());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("measuredAt", now.toString());
        snapshot.put("channel", channel);
        snapshot.put("stats", stats);
        return snapshot;
    }

    private void save(UUID analysisId, String status, List<VideoInfo> videos, Stats stats, String snapshot,
                      Output output, List<String> warnings, int maxVideos) {
        for (VideoInfo v : videos) {
            if (!"none".equals(v.liveStatus())) {
                continue;
            }
            String d = v.description();
            jdbc.update("""
                    insert into channel_video (analysis_id, youtube_video_id, title, description, published_at,
                        duration_seconds, views, likes, comments, format, tags, live_status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """, analysisId, v.videoId(), v.title(), d.length() > 1000 ? d.substring(0, 1000) : d,
                    Timestamp.from(v.publishedAt()), v.durationSeconds(), v.views(), v.likes(), v.comments(),
                    ChannelStats.formatOf(v), json.writeValueAsString(v.tags()), v.liveStatus());
        }
        List<String> limitations = new ArrayList<>(FIXED_LIMITATIONS);
        limitations.add(ChannelStats.BASIS_FEED.equals(stats.formatBasis())
                ? "Shorts and regular videos are told apart using YouTube's own Shorts list."
                : "YouTube's Shorts list was not available for this channel, so the split between Shorts and long "
                        + "videos is an estimate from video length.");
        limitations.add(stats.videosAnalysed() + " recent public videos were analysed: the " + maxVideos
                + " most recent for upload rhythm, plus at least 10 of each format (Shorts and regular videos) where the channel has them.");
        if (output != null) {
            limitations.addAll(output.limitations());
        }
        jdbc.update("""
                update channel_analysis set status = ?, completed_at = now(), videos_analysed = ?,
                    channel_stats = ?::jsonb, analysis = ?::jsonb, data_limitations = ?::jsonb, warnings = ?::jsonb
                where id = ?
                """, status, stats.videosAnalysed(), snapshot, output == null ? null : json.writeValueAsString(output),
                json.writeValueAsString(limitations), json.writeValueAsString(warnings), analysisId);
    }

    private void fail(UUID analysisId, String message, String snapshot) {
        jdbc.update("""
                update channel_analysis set status = 'FAILED', completed_at = now(), warnings = ?::jsonb,
                    channel_stats = ?::jsonb
                where id = ?
                """, json.writeValueAsString(List.of(message)), snapshot, analysisId);
    }

    private List<String> strings(String jsonArray) {
        List<String> out = new ArrayList<>();
        if (jsonArray != null) {
            for (JsonNode n : json.readTree(jsonArray)) {
                out.add(n.asString());
            }
        }
        return out;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
