package com.launchmate.channel;

import com.launchmate.ai.Quotes;
import com.launchmate.channel.ChannelStats.FormatStats;
import com.launchmate.channel.ChannelStats.Ranked;
import com.launchmate.channel.ChannelStats.Stats;
import com.launchmate.channel.ChannelStats.WordCount;
import com.launchmate.channel.YouTubeData.ChannelInfo;
import com.launchmate.channel.YouTubeData.VideoInfo;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the plain-text evidence the model reads for a channel analysis. It is plain text so the
 * model can quote lines exactly and the validator can confirm each quote and each video title.
 * Titles and descriptions are included verbatim and may contain instructions aimed at an automated
 * reader; the system prompt tells the model to treat all of it as data.
 */
public final class ChannelEvidence {

    public record Evidence(String text, Set<String> videoTitles) {}

    private static final int CHANNEL_DESCRIPTION_CHARS = 300;
    private static final int VIDEO_DESCRIPTION_CHARS = 200;

    private ChannelEvidence() {}

    public static Evidence build(ChannelInfo channel, List<VideoInfo> videos, Stats stats, List<String> ownerFacts,
                                 Instant now) {
        StringBuilder sb = new StringBuilder();
        sb.append("CHANNEL: ").append(line(channel.title())).append(" (")
                .append(channel.handle() == null ? "no handle" : channel.handle()).append(")\n");
        if (channel.publishedAt() != null) {
            sb.append("- created: ").append(date(channel.publishedAt())).append(" (")
                    .append(ago(channel.publishedAt(), now)).append(")\n");
        }
        sb.append("- country: ").append(channel.country() == null ? "(not given)" : channel.country()).append("\n");
        sb.append("- subscribers: ").append(channel.subscribersHidden() || channel.subscribers() == null
                ? "hidden by the channel" : n(channel.subscribers()) + " (YouTube rounds this number)").append("\n");
        sb.append("- total views: ").append(n(channel.totalViews())).append("\n");
        sb.append("- videos on the channel: ").append(n(channel.videoCount())).append("\n");
        String description = line(channel.description());
        sb.append("- description: ").append(description.isEmpty() ? "(none)"
                : cut(description, CHANNEL_DESCRIPTION_CHARS)).append("\n");

        if (!ownerFacts.isEmpty()) {
            sb.append("\nOWNER-PROVIDED INFORMATION (typed in by the creator; not verified):\n");
            for (String fact : ownerFacts) {
                sb.append("- ").append(cut(line(fact), 400)).append("\n");
            }
        }

        List<VideoInfo> shown = videos.stream().filter(v -> "none".equals(v.liveStatus())).toList();
        sb.append("\nANALYSED: the ").append(stats.videosAnalysed()).append(" most recent public video(s)");
        if (!shown.isEmpty()) {
            sb.append(", published between ").append(date(shown.get(shown.size() - 1).publishedAt())).append(" and ")
                    .append(date(shown.get(0).publishedAt()));
        }
        sb.append(".");
        if (stats.excludedLive() > 0) {
            sb.append(" ").append(stats.excludedLive()).append(" live or upcoming stream(s) were left out.");
        }
        sb.append("\n");

        cadence(sb, stats);
        formats(sb, stats);
        titles(sb, stats);

        sb.append("\nDESCRIPTIONS (measured):\n");
        int vids = stats.videosAnalysed();
        sb.append("- empty descriptions: ").append(stats.descriptions().empty()).append(" of ").append(vids)
                .append("; average length ").append(stats.descriptions().averageLength()).append(" characters\n");
        sb.append("- descriptions with a link: ").append(stats.descriptions().withLinks()).append(" of ").append(vids)
                .append("; with a hashtag: ").append(stats.descriptions().withHashtags()).append(" of ").append(vids)
                .append("\n");

        sb.append("\nTAGS (measured):\n- videos with tags: ").append(stats.tags().videosWithTags()).append(" of ")
                .append(vids).append("; most common tags: ").append(words(stats.tags().topTags())).append("\n");

        sb.append("\nENGAGEMENT (measured; only videos where YouTube returned the count):\n");
        sb.append("- median likes per 100 views: ").append(stats.engagement().likesPer100Views() == null
                ? "not available" : stats.engagement().likesPer100Views()).append(" (")
                .append(stats.engagement().likesAvailable()).append(" of ").append(vids).append(" videos)\n");
        sb.append("- median comments per 100 views: ").append(stats.engagement().commentsPer100Views() == null
                ? "not available" : stats.engagement().commentsPer100Views()).append(" (")
                .append(stats.engagement().commentsAvailable()).append(" of ").append(vids).append(" videos)\n");

        ranked(sb, "BEST VIDEOS BY VIEWS PER DAY (videos under 3 days old are left out)", stats.best());
        ranked(sb, "WEAKEST VIDEOS BY VIEWS PER DAY (videos under 3 days old are left out)", stats.weakest());

        sb.append("\nNOT AVAILABLE FROM PUBLIC YOUTUBE DATA: watch time, audience retention, impressions, "
                + "click-through rate, audience demographics, traffic sources, revenue.\n");

        Set<String> titles = new LinkedHashSet<>();
        int i = 0;
        for (VideoInfo v : shown) {
            i++;
            titles.add(line(v.title()));
            sb.append("\nVIDEO ").append(i).append(": ").append(line(v.title())).append(" (https://www.youtube.com/watch?v=")
                    .append(v.videoId()).append(")\n");
            sb.append("  published: ").append(date(v.publishedAt())).append(" (").append(ago(v.publishedAt(), now))
                    .append(")\n");
            String format = ChannelStats.formatOf(v);
            String basis = v.inShortsFeed() != null ? "format per YouTube's Shorts list: " : "estimated format: ";
            sb.append("  length: ").append(v.durationSeconds() == null ? "not available" : length(v.durationSeconds()))
                    .append(format == null ? "" : " (" + basis + format + ")").append("\n");
            sb.append("  views: ").append(v.views() == null ? "not available" : n(v.views())).append("\n");
            sb.append("  likes: ").append(v.likes() == null ? "not available" : n(v.likes())).append("\n");
            sb.append("  comments: ").append(v.comments() == null ? "not available" : n(v.comments())).append("\n");
            sb.append("  tags: ").append(v.tags().isEmpty() ? "(none)" : String.join(", ", v.tags())).append("\n");
            String d = line(v.description());
            sb.append("  description: ").append(d.isEmpty() ? "(none)" : cut(d, VIDEO_DESCRIPTION_CHARS)).append("\n");
        }
        return new Evidence(sb.toString(), Set.copyOf(titles));
    }

    private static void cadence(StringBuilder sb, Stats s) {
        ChannelStats.Cadence c = s.cadence();
        sb.append("\nCADENCE (measured):\n");
        if (c.uploadsPerWeek() == null) {
            sb.append("- not enough videos to measure how often the channel uploads\n");
        } else {
            sb.append("- uploads in the sample: ").append(c.videos()).append(" over ").append(c.spanDays())
                    .append(" days, about ").append(c.uploadsPerWeek()).append(" per week\n");
            sb.append("- gap between uploads: median ").append(c.medianGapDays()).append(" days, shortest ")
                    .append(c.shortestGapDays()).append(", longest ").append(c.longestGapDays()).append("\n");
        }
        sb.append("- days since the latest upload: ").append(c.daysSinceLatest()).append("\n");
    }

    private static void formats(StringBuilder sb, Stats s) {
        boolean observed = ChannelStats.BASIS_FEED.equals(s.formatBasis());
        sb.append(observed
                ? "\nFORMAT (observed: each video is a Short or a regular video according to YouTube's own Shorts list):\n"
                : "\nFORMAT (an estimate from video length; YouTube's Shorts list was not available):\n");
        for (FormatStats f : s.formats()) {
            if (observed && f.format().equals(ChannelStats.MID_LENGTH)) {
                continue;
            }
            String label = observed
                    ? (f.format().equals(ChannelStats.SHORT) ? "Shorts" : "regular (non-Short) videos")
                    : switch (f.format()) {
                        case ChannelStats.SHORT -> "60 seconds or less (Short length)";
                        case ChannelStats.MID_LENGTH -> "61 to 180 seconds (could be a Short or a regular video)";
                        default -> "over 180 seconds (long-form)";
                    };
            sb.append("- ").append(label).append(": ").append(f.videos()).append(" video(s)");
            if (f.videos() > 0 && f.medianViews() != null) {
                sb.append(", median views ").append(n(f.medianViews())).append(", median views per day ")
                        .append(f.medianViewsPerDay());
            }
            sb.append("\n");
        }
        if (s.unknownFormat() > 0) {
            sb.append("- length not available: ").append(s.unknownFormat()).append(" video(s)\n");
        }
    }

    private static void titles(StringBuilder sb, Stats s) {
        ChannelStats.TitleStyle t = s.titles();
        int n = t.titles();
        sb.append("\nTITLE STYLE (measured):\n");
        sb.append("- average title length: ").append(t.averageLength()).append(" characters\n");
        sb.append("- titles containing a number: ").append(t.withNumber()).append(" of ").append(n).append("\n");
        sb.append("- titles containing a question mark: ").append(t.withQuestion()).append(" of ").append(n).append("\n");
        sb.append("- titles with a word in all capitals (3 letters or more): ").append(t.withAllCapsWord())
                .append(" of ").append(n).append("\n");
        sb.append("- titles with brackets: ").append(t.withBrackets()).append(" of ").append(n).append("\n");
        sb.append("- titles with an emoji: ").append(t.withEmoji()).append(" of ").append(n).append("\n");
        sb.append("- titles with a pipe: ").append(t.withPipe()).append(" of ").append(n)
                .append("; with a colon: ").append(t.withColon()).append(" of ").append(n).append("\n");
        sb.append("- most repeated words in titles: ").append(words(t.topWords())).append("\n");
        sb.append("- most repeated opening words: ").append(words(t.repeatedFirstWords())).append("\n");
    }

    private static void ranked(StringBuilder sb, String heading, List<Ranked> list) {
        sb.append("\n").append(heading).append(":\n");
        if (list.isEmpty()) {
            sb.append("- not enough older videos to rank\n");
        }
        for (Ranked r : list) {
            sb.append("- ").append(line(r.title())).append(": ").append(r.viewsPerDay()).append(" views per day (")
                    .append(n(r.views())).append(" views, ").append(r.ageDays()).append(" days old)\n");
        }
    }

    private static String words(List<WordCount> list) {
        return list.isEmpty() ? "none repeated" : list.stream()
                .map(w -> w.word() + " (" + w.count() + ")").collect(Collectors.joining(", "));
    }

    private static String line(String s) {
        return Quotes.collapse(s);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String n(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    private static String date(Instant t) {
        return t.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static String ago(Instant t, Instant now) {
        long days = Duration.between(t, now).toDays();
        if (days < 1) {
            return "less than a day ago";
        }
        if (days < 60) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        return (days / 365 >= 2) ? (days / 365) + " years ago" : (days / 30) + " months ago";
    }

    private static String length(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? h + "h " + m + "m " + s + "s" : m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
