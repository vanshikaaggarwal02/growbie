package com.launchmate.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.channel.ChannelStats.FormatStats;
import com.launchmate.channel.ChannelStats.Ranked;
import com.launchmate.channel.ChannelStats.Stats;
import com.launchmate.channel.ChannelStats.WordCount;
import com.launchmate.channel.YouTubeData.VideoInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every expected value here was worked out by hand from the data below, not copied from the code. */
class ChannelStatsTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private static VideoInfo video(String id, long daysAgo, Integer seconds, Long views, Long likes, Long comments,
                                   String title, String description, List<String> tags, String live) {
        return new VideoInfo(id, title, description, NOW.minus(Duration.ofDays(daysAgo)), seconds, views, likes,
                comments, tags, live, "en");
    }

    private static List<VideoInfo> sample() {
        List<VideoInfo> v = new ArrayList<>();
        v.add(video("v1", 1, 45, 1000L, 100L, 10L, "5 tips to build faster?", "", List.of("a", "b"), "none"));
        v.add(video("v2", 8, 59, 200L, 20L, null, "BUILD with AI | Part 2", "Link: https://example.com #ai", List.of("a"), "none"));
        v.add(video("v3", 15, 61, 300L, null, null, "Why I build in public", "Short note", List.of(), "none"));
        v.add(video("v4", 22, 180, 400L, 40L, 4L, "Build a rover [Full guide]", "", List.of("a", "c"), "none"));
        v.add(video("v5", 29, 181, 500L, 25L, 5L, "🪴 Plant rover update", "#rover #build", List.of("c"), "none"));
        v.add(video("v6", 36, 600, 600L, 60L, 6L, "Gemini live demo", "A longer description here with words", List.of(), "none"));
        v.add(video("v7", 43, 1200, 700L, 35L, 7L, "Gemini tips and tricks", "x", List.of("a"), "none"));
        v.add(video("v8", 50, 30, 800L, 80L, 8L, "Gemini in 30 seconds", "", List.of(), "none"));
        v.add(video("live", 0, null, 9L, null, null, "Live now", "", List.of(), "live"));
        return v;
    }

    private static FormatStats format(Stats s, String name) {
        return s.formats().stream().filter(f -> f.format().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void liveVideosAreExcludedAndCounted() {
        Stats s = ChannelStats.compute(sample(), NOW);
        assertEquals(8, s.videosAnalysed());
        assertEquals(1, s.excludedLive());
    }

    @Test
    void measuresUploadCadenceFromRealDates() {
        ChannelStats.Cadence c = ChannelStats.compute(sample(), NOW).cadence();
        assertEquals(8, c.videos());
        assertEquals(49.0, c.spanDays());
        assertEquals(1.0, c.uploadsPerWeek(), "7 gaps over 49 days is one upload a week");
        assertEquals(7.0, c.medianGapDays());
        assertEquals(7.0, c.longestGapDays());
        assertEquals(7.0, c.shortestGapDays());
        assertEquals(1.0, c.daysSinceLatest());
    }

    @Test
    void cadenceReflectsUnevenGapsAndAnIdleChannel() {
        List<VideoInfo> v = List.of(
                video("a", 100, 300, 1L, 1L, 1L, "A", "", List.of(), "none"),
                video("b", 98, 300, 1L, 1L, 1L, "B", "", List.of(), "none"),
                video("c", 90, 300, 1L, 1L, 1L, "C", "", List.of(), "none"));
        ChannelStats.Cadence c = ChannelStats.compute(v, NOW).cadence();
        assertEquals(10.0, c.spanDays());
        assertEquals(2.0, c.shortestGapDays());
        assertEquals(8.0, c.longestGapDays());
        assertEquals(5.0, c.medianGapDays(), "median of 2 and 8");
        assertEquals(90.0, c.daysSinceLatest(), "the channel has been quiet for 90 days");
    }

    @Test
    void cadenceIsNotComputedFromASingleVideo() {
        ChannelStats.Cadence c = ChannelStats.compute(
                List.of(video("a", 5, 300, 10L, 1L, 1L, "A", "", List.of(), "none")), NOW).cadence();
        assertNull(c.spanDays());
        assertNull(c.uploadsPerWeek());
        assertNull(c.medianGapDays());
        assertEquals(5.0, c.daysSinceLatest());
    }

    @Test
    void splitsVideosByDurationIntoThreeEstimatedFormatsWithMedians() {
        Stats s = ChannelStats.compute(sample(), NOW);
        FormatStats shorts = format(s, "SHORT");
        assertEquals(3, shorts.videos(), "45 s, 59 s and 30 s are 60 seconds or less");
        assertEquals(800L, shorts.medianViews(), "views 1000, 200, 800");
        assertEquals(25.0, shorts.medianViewsPerDay(), "1000/1, 200/8, 800/50 = 1000, 25, 16");

        FormatStats mid = format(s, "MID_LENGTH");
        assertEquals(2, mid.videos(), "61 s and 180 s");
        assertEquals(350L, mid.medianViews());
        assertEquals(19.1, mid.medianViewsPerDay(), "(300/15 + 400/22) / 2");

        FormatStats longForm = format(s, "LONG_FORM");
        assertEquals(3, longForm.videos(), "181 s, 600 s and 1200 s");
        assertEquals(600L, longForm.medianViews());
        assertEquals(16.7, longForm.medianViewsPerDay());
    }

    @Test
    void measuresTitleStyle() {
        ChannelStats.TitleStyle t = ChannelStats.compute(sample(), NOW).titles();
        assertEquals(8, t.titles());
        assertEquals(21.3, t.averageLength(), "170 characters over 8 titles; the emoji counts as one character");
        assertEquals(3, t.withNumber());
        assertEquals(1, t.withQuestion());
        assertEquals(1, t.withAllCapsWord(), "BUILD counts; AI is too short to count");
        assertEquals(1, t.withBrackets());
        assertEquals(1, t.withEmoji());
        assertEquals(1, t.withPipe());
        assertEquals(0, t.withColon());
    }

    @Test
    void findsRepeatedWordsAndRepeatedOpeningsWithoutStopWords() {
        ChannelStats.TitleStyle t = ChannelStats.compute(sample(), NOW).titles();
        assertEquals(List.of(new WordCount("build", 4), new WordCount("gemini", 3), new WordCount("rover", 2),
                new WordCount("tips", 2)), t.topWords());
        assertEquals(List.of(new WordCount("gemini", 3), new WordCount("build", 2)), t.repeatedFirstWords());
    }

    @Test
    void measuresDescriptionsAndTags() {
        Stats s = ChannelStats.compute(sample(), NOW);
        assertEquals(3, s.descriptions().empty());
        assertEquals(11.1, s.descriptions().averageLength(), "89 characters over 8 descriptions");
        assertEquals(1, s.descriptions().withLinks());
        assertEquals(2, s.descriptions().withHashtags());
        assertEquals(5, s.tags().videosWithTags());
        assertEquals(List.of(new WordCount("a", 4), new WordCount("c", 2)), s.tags().topTags());
    }

    @Test
    void engagementUsesOnlyVideosWhereTheCountsExist() {
        ChannelStats.Engagement e = ChannelStats.compute(sample(), NOW).engagement();
        assertEquals(10.0, e.likesPer100Views());
        assertEquals(7, e.likesAvailable(), "one video has no like count");
        assertEquals(1.0, e.commentsPer100Views());
        assertEquals(6, e.commentsAvailable(), "two videos have no comment count");
        assertEquals(8, e.videos());
    }

    @Test
    void ranksBestAndWeakestByViewsPerDayIgnoringVideosUnderThreeDaysOld() {
        Stats s = ChannelStats.compute(sample(), NOW);
        assertEquals(List.of("v2", "v3", "v4"), s.best().stream().map(Ranked::videoId).toList());
        assertEquals(List.of("v8", "v7", "v6"), s.weakest().stream().map(Ranked::videoId).toList());
        assertEquals(25.0, s.best().get(0).viewsPerDay());
        assertEquals("SHORT", s.best().get(0).format());
        assertTrue(s.best().stream().noneMatch(r -> r.videoId().equals("v1")),
                "a one-day-old video is too new to rank fairly");
    }

    @Test
    void bestAndWeakestNeverOverlapWhenThereAreFewVideos() {
        List<VideoInfo> v = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            v.add(video("x" + i, 10 + i, 300, 100L * (i + 1), 1L, 1L, "T" + i, "", List.of(), "none"));
        }
        Stats s = ChannelStats.compute(v, NOW);
        List<String> best = s.best().stream().map(Ranked::videoId).toList();
        List<String> weakest = s.weakest().stream().map(Ranked::videoId).toList();
        assertTrue(best.stream().noneMatch(weakest::contains), best + " vs " + weakest);
        assertEquals(4, best.size() + weakest.size());
    }

    @Test
    void anEmptyChannelProducesZerosNotErrors() {
        Stats s = ChannelStats.compute(List.of(), NOW);
        assertEquals(0, s.videosAnalysed());
        assertNull(s.cadence().uploadsPerWeek());
        assertEquals(3, s.formats().size());
        assertTrue(s.best().isEmpty());
    }
}
