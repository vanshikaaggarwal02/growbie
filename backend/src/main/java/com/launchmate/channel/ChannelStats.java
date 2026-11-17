package com.launchmate.channel;

import com.launchmate.channel.YouTubeData.VideoInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything about a channel that can be measured by arithmetic from public data, with no AI. The
 * model only ever interprets these numbers; it never produces them. Views per day divides a video's
 * views by its age (at least one day), which makes old and new videos roughly comparable but is
 * only an arithmetic ratio, not a measure of how well a video performed.
 */
public final class ChannelStats {

    public record Cadence(int videos, Double spanDays, Double uploadsPerWeek, Double medianGapDays,
                          Double longestGapDays, Double shortestGapDays, Double averageGapDays,
                          double daysSinceLatest) {}

    /** {@code format} is a duration-based estimate: YouTube does not mark which videos are Shorts. */
    public record FormatStats(String format, int videos, Long medianViews, Double medianViewsPerDay,
                              Double likesPer100Views, Double commentsPer100Views) {}

    public record WordCount(String word, int count) {}

    public record TitleStyle(int titles, double averageLength, int withNumber, int withQuestion,
                             int withAllCapsWord, int withBrackets, int withEmoji, int withPipe, int withColon,
                             List<WordCount> topWords, List<WordCount> repeatedFirstWords) {}

    public record DescriptionStyle(int videos, int empty, double averageLength, int withLinks, int withHashtags) {}

    public record TagStyle(int videosWithTags, List<WordCount> topTags) {}

    public record Engagement(Double likesPer100Views, int likesAvailable, Double commentsPer100Views,
                             int commentsAvailable, int videos) {}

    public record Ranked(String videoId, String title, long views, double viewsPerDay, double ageDays, String format) {}

    public record Stats(int videosAnalysed, int excludedLive, Cadence cadence, List<FormatStats> formats,
                        int unknownFormat, TitleStyle titles, DescriptionStyle descriptions, TagStyle tags,
                        Engagement engagement, List<Ranked> best, List<Ranked> weakest,
                        String formatBasis) {}

    /** Formats come from YouTube's own Shorts list. */
    public static final String BASIS_FEED = "YOUTUBE_SHORTS_LIST";
    /** Formats are guessed from video length. */
    public static final String BASIS_DURATION = "DURATION_ESTIMATE";

    public static final String SHORT = "SHORT";
    public static final String MID_LENGTH = "MID_LENGTH";
    public static final String LONG_FORM = "LONG_FORM";

    private static final int SHORT_MAX_SECONDS = 60;
    private static final int MID_MAX_SECONDS = 180;
    private static final double MIN_RANK_AGE_DAYS = 3.0;
    private static final Pattern EMOJI = Pattern.compile("[\\p{IsEmoji_Presentation}\\p{IsExtended_Pictographic}]");
    private static final Pattern CAPS_WORD = Pattern.compile("\\b\\p{Lu}{3,}\\b");
    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "are", "but", "can", "for", "from", "has", "have", "how", "its", "new", "not", "our", "that",
            "the", "their", "this", "was", "what", "when", "why", "will", "with", "you", "your", "all", "any",
            "get", "got", "one", "out", "who", "vs", "into", "about", "than", "then", "them", "they");

    private ChannelStats() {}

    public static Stats compute(List<VideoInfo> all, Instant now) {
        return compute(all, now, all.stream().allMatch(v -> v.inShortsFeed() != null) && !all.isEmpty());
    }

    public static Stats compute(List<VideoInfo> all, Instant now, boolean formatsObserved) {
        return compute(all, now, formatsObserved, Integer.MAX_VALUE);
    }

    /** {@code cadenceWindow} limits upload rhythm to the newest N videos; other figures use every video given. */
    public static Stats compute(List<VideoInfo> all, Instant now, boolean formatsObserved, int cadenceWindow) {
        List<VideoInfo> videos = all.stream().filter(v -> "none".equals(v.liveStatus())).toList();
        int excluded = all.size() - videos.size();

        List<FormatStats> formats = new ArrayList<>();
        int unknownFormat = 0;
        for (String f : List.of(SHORT, MID_LENGTH, LONG_FORM)) {
            List<VideoInfo> group = videos.stream().filter(v -> f.equals(formatOf(v))).toList();
            Engagement e = engagement(group);
            formats.add(new FormatStats(f, group.size(), medianViews(group), medianViewsPerDay(group, now),
                    e.likesPer100Views(), e.commentsPer100Views()));
        }
        for (VideoInfo v : videos) {
            if (formatOf(v) == null) {
                unknownFormat++;
            }
        }

        List<VideoInfo> recentWindow = videos.stream()
                .sorted(java.util.Comparator.comparing(VideoInfo::publishedAt).reversed()).limit(cadenceWindow).toList();
        return new Stats(videos.size(), excluded, cadence(recentWindow, now), List.copyOf(formats), unknownFormat,
                titles(videos), descriptions(videos), tags(videos), engagement(videos),
                ranking(videos, now, true), ranking(videos, now, false),
                formatsObserved ? BASIS_FEED : BASIS_DURATION);
    }

    /**
     * The format of one video: SHORT or LONG_FORM when YouTube's own Shorts list says so, otherwise
     * the estimate from length. Null if neither is known.
     */
    public static String formatOf(VideoInfo v) {
        if (v.inShortsFeed() != null) {
            return v.inShortsFeed() ? SHORT : LONG_FORM;
        }
        return formatOf(v.durationSeconds());
    }

    /** SHORT is 60 seconds or less, MID_LENGTH is 61 to 180 seconds, LONG_FORM is longer; null if unknown. */
    public static String formatOf(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        return seconds <= SHORT_MAX_SECONDS ? SHORT : seconds <= MID_MAX_SECONDS ? MID_LENGTH : LONG_FORM;
    }

    // ---- cadence ----------------------------------------------------------------------------

    private static Cadence cadence(List<VideoInfo> videos, Instant now) {
        if (videos.isEmpty()) {
            return new Cadence(0, null, null, null, null, null, null, 0.0);
        }
        List<Instant> times = videos.stream().map(VideoInfo::publishedAt).sorted().toList();
        double sinceLatest = round1(days(times.get(times.size() - 1), now));
        if (times.size() < 2) {
            return new Cadence(times.size(), null, null, null, null, null, null, sinceLatest);
        }
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            gaps.add(days(times.get(i - 1), times.get(i)));
        }
        double span = days(times.get(0), times.get(times.size() - 1));
        double average = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        Double perWeek = span > 0 ? round1((times.size() - 1) / (span / 7.0)) : null;
        return new Cadence(times.size(), round1(span), perWeek, round1(median(gaps)),
                round1(Collections.max(gaps)), round1(Collections.min(gaps)), round1(average), sinceLatest);
    }

    // ---- titles, descriptions, tags ---------------------------------------------------------

    private static TitleStyle titles(List<VideoInfo> videos) {
        int number = 0;
        int question = 0;
        int caps = 0;
        int brackets = 0;
        int emoji = 0;
        int pipe = 0;
        int colon = 0;
        long chars = 0;
        Map<String, Integer> words = new HashMap<>();
        Map<String, Integer> firstWords = new HashMap<>();
        for (VideoInfo v : videos) {
            String t = v.title();
            chars += t.codePointCount(0, t.length());
            number += t.chars().anyMatch(Character::isDigit) ? 1 : 0;
            question += t.contains("?") ? 1 : 0;
            caps += CAPS_WORD.matcher(t).find() ? 1 : 0;
            brackets += t.matches("(?s).*[\\[\\](){}【】].*") ? 1 : 0;
            emoji += EMOJI.matcher(t).find() ? 1 : 0;
            pipe += t.contains("|") ? 1 : 0;
            colon += t.contains(":") ? 1 : 0;

            List<String> tokens = new ArrayList<>();
            for (String w : NON_LETTERS.split(t.toLowerCase(Locale.ROOT))) {
                if (w.codePointCount(0, w.length()) >= 3 && !STOP_WORDS.contains(w)) {
                    tokens.add(w);
                }
            }
            tokens.forEach(w -> words.merge(w, 1, Integer::sum));
            String first = firstToken(t);
            if (first != null) {
                firstWords.merge(first, 1, Integer::sum);
            }
        }
        double avg = videos.isEmpty() ? 0 : round1((double) chars / videos.size());
        return new TitleStyle(videos.size(), avg, number, question, caps, brackets, emoji, pipe, colon,
                top(words, 2, 8), top(firstWords, 2, 3));
    }

    private static String firstToken(String title) {
        for (String w : NON_LETTERS.split(title.toLowerCase(Locale.ROOT))) {
            if (!w.isEmpty()) {
                return w;
            }
        }
        return null;
    }

    private static DescriptionStyle descriptions(List<VideoInfo> videos) {
        int empty = 0;
        int links = 0;
        int hashtags = 0;
        long chars = 0;
        for (VideoInfo v : videos) {
            String d = v.description();
            chars += d.codePointCount(0, d.length());
            empty += d.isBlank() ? 1 : 0;
            links += d.contains("http://") || d.contains("https://") ? 1 : 0;
            hashtags += d.contains("#") ? 1 : 0;
        }
        return new DescriptionStyle(videos.size(), empty,
                videos.isEmpty() ? 0 : round1((double) chars / videos.size()), links, hashtags);
    }

    private static TagStyle tags(List<VideoInfo> videos) {
        int withTags = 0;
        Map<String, Integer> counts = new HashMap<>();
        for (VideoInfo v : videos) {
            if (!v.tags().isEmpty()) {
                withTags++;
            }
            for (String t : v.tags()) {
                counts.merge(t.toLowerCase(Locale.ROOT).trim(), 1, Integer::sum);
            }
        }
        return new TagStyle(withTags, top(counts, 2, 8));
    }

    // ---- engagement and ranking -------------------------------------------------------------

    private static Engagement engagement(List<VideoInfo> videos) {
        List<Double> likes = new ArrayList<>();
        List<Double> comments = new ArrayList<>();
        for (VideoInfo v : videos) {
            if (v.views() != null && v.views() > 0) {
                if (v.likes() != null) {
                    likes.add(v.likes() * 100.0 / v.views());
                }
                if (v.comments() != null) {
                    comments.add(v.comments() * 100.0 / v.views());
                }
            }
        }
        return new Engagement(likes.isEmpty() ? null : round1(median(likes)), likes.size(),
                comments.isEmpty() ? null : round1(median(comments)), comments.size(), videos.size());
    }

    private static List<Ranked> ranking(List<VideoInfo> videos, Instant now, boolean best) {
        List<Ranked> eligible = new ArrayList<>();
        for (VideoInfo v : videos) {
            double age = days(v.publishedAt(), now);
            if (v.views() != null && age >= MIN_RANK_AGE_DAYS) {
                eligible.add(new Ranked(v.videoId(), v.title(), v.views(), round1(v.views() / Math.max(1.0, age)),
                        round1(age), formatOf(v)));
            }
        }
        eligible.sort(Comparator.comparingDouble(Ranked::viewsPerDay).reversed()
                .thenComparing(Ranked::videoId));
        // With few videos the two lists share the pool, so they never overlap.
        int each = Math.min(3, eligible.size() / 2 + (best ? eligible.size() % 2 : 0));
        if (best) {
            return List.copyOf(eligible.subList(0, each));
        }
        List<Ranked> bottom = new ArrayList<>(eligible.subList(Math.max(each == 0 ? eligible.size() : 0,
                eligible.size() - Math.min(3, eligible.size() / 2)), eligible.size()));
        Collections.reverse(bottom);
        return List.copyOf(bottom);
    }

    // ---- small helpers ----------------------------------------------------------------------

    private static Long medianViews(List<VideoInfo> group) {
        List<Double> views = group.stream().filter(v -> v.views() != null).map(v -> v.views().doubleValue()).toList();
        return views.isEmpty() ? null : Math.round(median(views));
    }

    private static Double medianViewsPerDay(List<VideoInfo> group, Instant now) {
        List<Double> rates = group.stream().filter(v -> v.views() != null)
                .map(v -> v.views() / Math.max(1.0, days(v.publishedAt(), now))).toList();
        return rates.isEmpty() ? null : round1(median(rates));
    }

    private static List<WordCount> top(Map<String, Integer> counts, int minCount, int limit) {
        return counts.entrySet().stream().filter(e -> e.getValue() >= minCount)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit).map(e -> new WordCount(e.getKey(), e.getValue())).toList();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double days(Instant from, Instant to) {
        return Duration.between(from, to).toSeconds() / 86400.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
