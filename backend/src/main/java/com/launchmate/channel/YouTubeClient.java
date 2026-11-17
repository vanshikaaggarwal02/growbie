package com.launchmate.channel;

import com.launchmate.channel.YouTubeData.ChannelInfo;
import com.launchmate.channel.YouTubeData.RecentVideos;
import com.launchmate.channel.YouTubeData.VideoInfo;
import com.launchmate.common.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * YouTube Data API v3, public data only, using the cheap endpoints (channels.list, playlistItems.list
 * and videos.list cost 1 quota unit each; search.list costs 100 and is never used). One analysis of
 * 20 videos costs 3 units out of the 10,000 a day. The API key is in the request URL, so URLs are
 * never logged.
 */
public class YouTubeClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeClient.class);
    private static final int PAGE_SIZE = 50;
    private static final int MIN_PER_FORMAT = 10;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper json;

    public YouTubeClient(String baseUrl, String apiKey, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.json = json;
    }

    public ChannelInfo channel(ChannelRef ref) {
        String lookup = switch (ref) {
            case ChannelRef.Handle h -> "&forHandle=" + enc("@" + h.handle());
            case ChannelRef.Id i -> "&id=" + enc(i.channelId());
            case ChannelRef.Username u -> "&forUsername=" + enc(u.username());
            case ChannelRef.Custom c -> "&forHandle=" + enc("@" + c.name());
        };
        JsonNode items = get("/youtube/v3/channels?part=snippet,statistics,contentDetails" + lookup).path("items");
        if ((!items.isArray() || items.isEmpty()) && ref instanceof ChannelRef.Custom c) {
            // Older custom names are sometimes the legacy username instead of the handle.
            items = get("/youtube/v3/channels?part=snippet,statistics,contentDetails&forUsername=" + enc(c.name())).path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "YOUTUBE_CHANNEL_NOT_FOUND",
                        "That custom link (youtube.com/" + c.name() + ") could not be matched to a channel. Open the "
                                + "channel on YouTube and paste its link that starts with youtube.com/@ instead.");
            }
        }
        if (!items.isArray() || items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "YOUTUBE_CHANNEL_NOT_FOUND",
                    "No YouTube channel was found for that link.");
        }
        JsonNode c = items.get(0);
        JsonNode stats = c.path("statistics");
        boolean hidden = stats.path("hiddenSubscriberCount").asBoolean(false);
        String uploads = text(c.path("contentDetails").path("relatedPlaylists"), "uploads");
        if (uploads == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "YOUTUBE_CHANNEL_NOT_FOUND",
                    "That channel has no public videos to analyse.");
        }
        return new ChannelInfo(text(c, "id"), nz(text(c.path("snippet"), "title")), text(c.path("snippet"), "customUrl"),
                nz(text(c.path("snippet"), "description")), text(c.path("snippet"), "country"),
                instant(text(c.path("snippet"), "publishedAt")),
                hidden ? null : number(stats, "subscriberCount"), hidden,
                orZero(number(stats, "viewCount")), orZero(number(stats, "videoCount")), uploads);
    }

    private record Entry(String videoId, Instant publishedAt, Boolean shortsFeed) {}

    private record Feed(List<Entry> entries, Long total) {}

    /**
     * The newest public videos, newest first. YouTube keeps a Shorts-only list (UUSH...) and a
     * regular-videos list (UULF...) for each channel, both reachable with the same 1-unit
     * playlistItems call. Reading them tells us for certain which videos are Shorts; if either is
     * unavailable we fall back to the uploads list and the format is an estimate from length.
     */
    public RecentVideos recentVideos(ChannelInfo channel, int max) {
        String id = channel.channelId();
        List<Entry> chosen = null;
        Long shortsTotal = null;
        Long longTotal = null;
        boolean observed = false;
        if (id != null && id.startsWith("UC") && id.length() > 2) {
            Feed shorts = feed("UUSH" + id.substring(2), max, true);
            Feed regular = feed("UULF" + id.substring(2), max, false);
            if (shorts != null && regular != null) {
                List<Entry> merged = new ArrayList<>(shorts.entries());
                merged.addAll(regular.entries());
                merged.sort(Comparator.comparing(Entry::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
                chosen = new ArrayList<>(merged.subList(0, Math.min(max, merged.size())));
                // A channel that posts mostly one kind would leave the other with too few videos to
                // compare, so top each kind up to a minimum from its own list.
                for (Feed feed : List.of(shorts, regular)) {
                    long have = chosen.stream().filter(e -> feed.entries().contains(e)).count();
                    for (Entry e : feed.entries()) {
                        if (have >= MIN_PER_FORMAT) {
                            break;
                        }
                        if (!chosen.contains(e)) {
                            chosen.add(e);
                            have++;
                        }
                    }
                }
                shortsTotal = shorts.total();
                longTotal = regular.total();
                observed = true;
            }
        }
        if (chosen == null) {
            Feed uploads = feed(channel.uploadsPlaylistId(), max, null);
            chosen = uploads == null ? List.of() : uploads.entries();
        }
        List<VideoInfo> videos = details(chosen);
        return new RecentVideos(videos, shortsTotal, longTotal, observed);
    }

    /** Returns null if the playlist does not exist (for example a channel with no Shorts has no Shorts list). */
    private Feed feed(String playlistId, int max, Boolean shortsFeed) {
        List<Entry> entries = new ArrayList<>();
        Long total = null;
        String pageToken = null;
        while (entries.size() < max) {
            int size = Math.min(PAGE_SIZE, max - entries.size());
            JsonNode page = getOrNullIfMissing("/youtube/v3/playlistItems?part=contentDetails&maxResults=" + size
                    + "&playlistId=" + enc(playlistId) + (pageToken == null ? "" : "&pageToken=" + enc(pageToken)));
            if (page == null) {
                // A missing Shorts or videos list means the channel has none of that kind.
                return shortsFeed == null ? null : new Feed(List.of(), 0L);
            }
            if (total == null && page.path("pageInfo").hasNonNull("totalResults")) {
                total = page.path("pageInfo").get("totalResults").asLong();
            }
            for (JsonNode item : page.path("items")) {
                JsonNode cd = item.path("contentDetails");
                String id = text(cd, "videoId");
                if (id != null && entries.size() < max) {
                    entries.add(new Entry(id, instant(text(cd, "videoPublishedAt")), shortsFeed));
                }
            }
            pageToken = text(page, "nextPageToken");
            if (pageToken == null) {
                break;
            }
        }
        return new Feed(entries, total);
    }

    private List<VideoInfo> details(List<Entry> entries) {
        Map<String, Entry> entryById = new HashMap<>();
        List<String> ids = new ArrayList<>();
        for (Entry e : entries) {
            if (entryById.putIfAbsent(e.videoId(), e) == null) {
                ids.add(e.videoId());
            }
        }
        Map<String, VideoInfo> byId = new HashMap<>();
        for (int i = 0; i < ids.size(); i += PAGE_SIZE) {
            List<String> chunk = ids.subList(i, Math.min(i + PAGE_SIZE, ids.size()));
            JsonNode videos = get("/youtube/v3/videos?part=snippet,contentDetails,statistics&maxResults=" + chunk.size()
                    + "&id=" + enc(String.join(",", chunk)));
            for (JsonNode v : videos.path("items")) {
                VideoInfo info = video(v);
                if (info != null) {
                    byId.put(info.videoId(), info);
                }
            }
        }
        List<VideoInfo> out = new ArrayList<>();
        for (String id : ids) {
            VideoInfo info = byId.get(id);
            if (info != null) {
                out.add(withFeed(info, entryById.get(id).shortsFeed()));
            }
        }
        out.sort(Comparator.comparing(VideoInfo::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private static VideoInfo withFeed(VideoInfo v, Boolean shortsFeed) {
        return new VideoInfo(v.videoId(), v.title(), v.description(), v.publishedAt(), v.durationSeconds(), v.views(),
                v.likes(), v.comments(), v.tags(), v.liveStatus(), v.language(), shortsFeed);
    }

    private static VideoInfo video(JsonNode v) {
        JsonNode s = v.path("snippet");
        String id = text(v, "id");
        Instant published = instant(text(s, "publishedAt"));
        if (id == null || published == null) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode t : s.path("tags")) {
            if (t.isString()) {
                tags.add(t.asString());
            }
        }
        JsonNode stats = v.path("statistics");
        String live = text(s, "liveBroadcastContent");
        String lang = text(s, "defaultAudioLanguage") != null ? text(s, "defaultAudioLanguage") : text(s, "defaultLanguage");
        return new VideoInfo(id, nz(text(s, "title")), nz(text(s, "description")), published,
                seconds(text(v.path("contentDetails"), "duration")), number(stats, "viewCount"),
                number(stats, "likeCount"), number(stats, "commentCount"), List.copyOf(tags),
                live == null ? "none" : live, lang);
    }

    /** Like {@link #get} but returns null when YouTube says the playlist does not exist. */
    private JsonNode getOrNullIfMissing(String pathAndQuery) {
        try {
            return get(pathAndQuery);
        } catch (ApiException e) {
            if ("YOUTUBE_PLAYLIST_NOT_FOUND".equals(e.getCode())) {
                return null;
            }
            throw e;
        }
    }

    private JsonNode get(String pathAndQuery) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "YOUTUBE_PROVIDER_ERROR",
                    "YouTube lookups are not configured.");
        }
        String url = baseUrl + pathAndQuery + "&key=" + enc(apiKey);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/json").GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return json.readTree(response.body());
                }
                String reason = errorReason(response.body());
                log.warn("YouTube request failed with status {} reason {} (attempt {})", status, reason, attempt);
                if (status == 403 && (reason.equals("quotaExceeded") || reason.equals("dailyLimitExceeded")
                        || reason.equals("rateLimitExceeded"))) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "YOUTUBE_QUOTA_EXCEEDED",
                            "The YouTube daily limit has been reached. Please try again tomorrow.");
                }
                if (status == 404 && reason.equals("playlistNotFound")) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "YOUTUBE_PLAYLIST_NOT_FOUND", "Playlist not found.");
                }
                if (status < 500) {
                    throw providerError();
                }
            } catch (IOException e) {
                log.warn("YouTube request failed: {} (attempt {})", e.getClass().getSimpleName(), attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw providerError();
            }
            if (attempt == 1) {
                pause(800);
            }
        }
        throw providerError();
    }

    private String errorReason(String body) {
        try {
            JsonNode reason = json.readTree(body).path("error").path("errors").path(0).path("reason");
            return reason.isString() ? reason.asString() : "unknown";
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static ApiException providerError() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "YOUTUBE_PROVIDER_ERROR",
                "YouTube could not be reached right now. Please try again.");
    }

    private static Integer seconds(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            long s = Duration.parse(iso).getSeconds();
            return s <= 0 || s > Integer.MAX_VALUE ? null : (int) s;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Long number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return Long.parseLong(v.asString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !v.isString() ? null : v.asString();
    }

    private static Instant instant(String s) {
        try {
            return s == null ? null : Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static long orZero(Long v) {
        return v == null ? 0 : v;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
