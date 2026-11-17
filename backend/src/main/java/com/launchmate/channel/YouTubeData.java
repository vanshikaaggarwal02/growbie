package com.launchmate.channel;

import java.time.Instant;
import java.util.List;

/** What the YouTube Data API returns about a channel and its videos, as plain values. */
public final class YouTubeData {

    private YouTubeData() {}

    /**
     * {@code subscribers} is null when the channel hides its count. YouTube also rounds this number
     * to three significant digits, so it is approximate even when shown.
     */
    public record ChannelInfo(String channelId, String title, String handle, String description, String country,
                              Instant publishedAt, Long subscribers, boolean subscribersHidden, long totalViews,
                              long videoCount, String uploadsPlaylistId) {}

    /**
     * Likes, comments and tags are null when the API did not return them; that means not available,
     * never zero. {@code durationSeconds} is null for live and upcoming videos.
     */
    public record VideoInfo(String videoId, String title, String description, Instant publishedAt,
                            Integer durationSeconds, Long views, Long likes, Long comments, List<String> tags,
                            String liveStatus, String language, Boolean inShortsFeed) {

        /** {@code inShortsFeed} is null when YouTube's Shorts list could not be used for this channel. */
        public VideoInfo(String videoId, String title, String description, Instant publishedAt,
                         Integer durationSeconds, Long views, Long likes, Long comments, List<String> tags,
                         String liveStatus, String language) {
            this(videoId, title, description, publishedAt, durationSeconds, views, likes, comments, tags,
                    liveStatus, language, null);
        }
    }

    /**
     * The recent videos plus how many Shorts and regular videos the whole channel has. Totals are null
     * and {@code formatsObserved} is false when YouTube's Shorts and videos lists were unavailable, in
     * which case the format of each video is only an estimate from its length.
     */
    public record RecentVideos(List<VideoInfo> videos, Long shortsTotal, Long longFormTotal, boolean formatsObserved) {}
}
