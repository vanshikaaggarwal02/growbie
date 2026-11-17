package com.launchmate.channel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Request and response shapes for the YouTube channel analysis API (LLD section 6.4). */
public final class ChannelAnalysisDtos {

    private ChannelAnalysisDtos() {}

    public record StartAnalysisRequest(@Min(5) @Max(50) Integer maxVideos) {}

    public record StartAnalysisResponse(UUID analysisId, String status) {}

    /** {@code format} is a duration-based estimate. Null counts mean YouTube did not return them. */
    public record VideoView(String videoId, String title, Instant publishedAt, Integer durationSeconds,
                            String format, Long views, Long likes, Long comments) {}

    /**
     * {@code channel} and {@code stats} are the measured snapshot taken when the analysis ran;
     * {@code analysis} is the model's interpretation and is null if it was unavailable.
     */
    public record ChannelAnalysisView(UUID analysisId, UUID profileId, String status, Instant createdAt,
                                      Instant startedAt, Instant completedAt, JsonNode channel, JsonNode stats,
                                      JsonNode analysis, List<VideoView> videos, List<String> limitations,
                                      List<String> warnings) {}

    /** One measured snapshot, so growth can be compared over time. */
    public record SnapshotItem(UUID analysisId, String status, Instant createdAt, Instant measuredAt,
                               Long subscribers, boolean subscribersHidden, Long totalViews, Long videoCount,
                               Double uploadsPerWeek, Integer videosAnalysed) {}
}
