package com.launchmate.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.channel.YouTubeData;
import com.launchmate.channel.YouTubeData.ChannelInfo;
import com.launchmate.channel.YouTubeData.VideoInfo;
import com.launchmate.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Replays trimmed real YouTube responses, plus the awkward cases, from a fake local server. */
class YouTubeClientTest {

    private record Reply(int status, String body) {}

    private HttpServer server;
    private String base;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    /** path and decoded query to reply; replaced per test. */
    private volatile BiFunction<String, String, Reply> handler;

    private static String fixture(String name) throws Exception {
        try (InputStream in = YouTubeClientTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void start() throws Exception {
        handler = (path, query) -> switch (path) {
            case "/youtube/v3/channels" -> new Reply(200, read("yt-channel.json"));
            case "/youtube/v3/playlistItems" -> new Reply(200, read("yt-playlist.json"));
            case "/youtube/v3/videos" -> new Reply(200, read("yt-videos.json"));
            default -> new Reply(404, "{}");
        };
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", ex -> {
            URI uri = ex.getRequestURI();
            String query = uri.getRawQuery() == null ? "" : URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
            requests.add(uri.getPath() + "?" + query);
            Reply r = handler.apply(uri.getPath(), query);
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String read(String name) {
        try {
            return fixture(name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private YouTubeClient client(String key) {
        return new YouTubeClient(base, key, JsonMapper.builder().build());
    }

    private static String param(String request, String name) {
        for (String p : request.substring(request.indexOf('?') + 1).split("&")) {
            if (p.startsWith(name + "=")) {
                return p.substring(name.length() + 1);
            }
        }
        return null;
    }

    @Test
    void readsARealChannelResponse() {
        ChannelInfo c = client("test-key").channel(new ChannelRef.Handle("GoogleDevelopers"));
        assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", c.channelId());
        assertEquals("Google for Developers", c.title());
        assertEquals("@googledevelopers", c.handle());
        assertEquals("US", c.country());
        assertEquals(2007, c.publishedAt().atZone(java.time.ZoneOffset.UTC).getYear());
        assertEquals(2_680_000L, c.subscribers());
        assertFalse(c.subscribersHidden());
        assertEquals(387_873_607L, c.totalViews());
        assertEquals(6126L, c.videoCount());
        assertEquals("UU_x5XG1OV2P6uZZ5FSM9Ttw", c.uploadsPlaylistId());

        String request = requests.get(0);
        assertTrue(request.startsWith("/youtube/v3/channels?"), request);
        assertEquals("snippet,statistics,contentDetails", param(request, "part"));
        assertEquals("@GoogleDevelopers", param(request, "forHandle"));
        assertEquals("test-key", param(request, "key"));
    }

    @Test
    void lookupUsesIdOrUsernameWhenGiven() {
        client("k").channel(new ChannelRef.Id("UC_x5XG1OV2P6uZZ5FSM9Ttw"));
        client("k").channel(new ChannelRef.Username("oldname"));
        assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", param(requests.get(0), "id"));
        assertEquals("oldname", param(requests.get(1), "forUsername"));
    }

    @Test
    void readsRealVideosNewestFirstWithOptionalFieldsAsNullWhenAbsent() {
        YouTubeClient client = client("k");
        ChannelInfo channel = client.channel(new ChannelRef.Handle("GoogleDevelopers"));
        List<VideoInfo> videos = client.recentVideos(uploadsOnly(channel), 8).videos();

        assertEquals(8, videos.size());
        for (int i = 1; i < videos.size(); i++) {
            assertFalse(videos.get(i).publishedAt().isAfter(videos.get(i - 1).publishedAt()), "newest first");
        }
        VideoInfo first = videos.stream().filter(v -> v.videoId().equals("vLAegkGGWrM")).findFirst().orElseThrow();
        assertEquals("Building with Gemini in Google Antigravity", first.title());
        assertEquals(63, first.durationSeconds());
        assertEquals(17_439L, first.views());
        assertEquals(158L, first.likes());
        assertEquals(12L, first.comments());
        assertEquals(2, first.tags().size());
        assertEquals("none", first.liveStatus());
        assertEquals("en", first.language());
        assertTrue(videos.stream().anyMatch(v -> v.title().startsWith("🪴")), "emoji titles are kept");
        assertFalse(requests.stream().anyMatch(r -> r.contains("/search")), "search.list (100 units) is never used");
    }

    @Test
    void aChannelThatHidesItsSubscriberCountHasNullNotZero() {
        handler = (path, query) -> new Reply(200, """
                {"items":[{"id":"UC_x5XG1OV2P6uZZ5FSM9Ttw","snippet":{"title":"Quiet","publishedAt":"2020-01-01T00:00:00Z"},
                 "statistics":{"viewCount":"1000","hiddenSubscriberCount":true,"videoCount":"12"},
                 "contentDetails":{"relatedPlaylists":{"uploads":"UUquiet"}}}]}""");
        ChannelInfo c = client("k").channel(new ChannelRef.Handle("quiet"));
        assertTrue(c.subscribersHidden());
        assertNull(c.subscribers());
        assertEquals(1000L, c.totalViews());
    }

    @Test
    void missingLikesCommentsTagsAndLiveDurationsAreNullNotZero() {
        handler = (path, query) -> switch (path) {
            case "/youtube/v3/channels" -> new Reply(200, read("yt-channel.json"));
            case "/youtube/v3/playlistItems" -> new Reply(200,
                    "{\"items\":[{\"contentDetails\":{\"videoId\":\"a\"}},{\"contentDetails\":{\"videoId\":\"b\"}}]}");
            default -> new Reply(200, """
                    {"items":[
                     {"id":"a","snippet":{"publishedAt":"2026-05-02T10:00:00Z","title":"No extras","liveBroadcastContent":"none"},
                      "contentDetails":{"duration":"PT10M"},"statistics":{"viewCount":"500"}},
                     {"id":"b","snippet":{"publishedAt":"2026-05-01T10:00:00Z","title":"Live now","liveBroadcastContent":"live"},
                      "contentDetails":{"duration":"P0D"},"statistics":{"viewCount":"9"}}]}""");
        };
        YouTubeClient client = client("k");
        List<VideoInfo> v = client.recentVideos(uploadsOnly(client.channel(new ChannelRef.Handle("x"))), 5).videos();

        VideoInfo plain = v.get(0);
        assertNull(plain.likes());
        assertNull(plain.comments());
        assertEquals(List.of(), plain.tags());
        assertEquals(600, plain.durationSeconds());
        VideoInfo live = v.get(1);
        assertNull(live.durationSeconds(), "a live video has no real duration");
        assertEquals("live", live.liveStatus());
    }

    @Test
    void anUnknownChannelIsReportedAsNotFound() {
        handler = (path, query) -> new Reply(200, "{\"kind\":\"youtube#channelListResponse\"}");
        ApiException e = assertThrows(ApiException.class, () -> client("k").channel(new ChannelRef.Handle("nobody")));
        assertEquals("YOUTUBE_CHANNEL_NOT_FOUND", e.getCode());
    }

    @Test
    void quotaErrorsAreRecognisedAndNotRetried() {
        handler = (path, query) -> new Reply(403,
                "{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"quotaExceeded\"}]}}");
        ApiException e = assertThrows(ApiException.class, () -> client("k").channel(new ChannelRef.Handle("x")));
        assertEquals("YOUTUBE_QUOTA_EXCEEDED", e.getCode());
        assertEquals(1, requests.size());
    }

    @Test
    void otherClientErrorsAreProviderErrorsWithoutRetryAndServerErrorsRetryOnce() {
        handler = (path, query) -> new Reply(400, "{\"error\":{\"errors\":[{\"reason\":\"keyInvalid\"}]}}");
        ApiException bad = assertThrows(ApiException.class, () -> client("k").channel(new ChannelRef.Handle("x")));
        assertEquals("YOUTUBE_PROVIDER_ERROR", bad.getCode());
        assertEquals(1, requests.size());

        requests.clear();
        handler = (path, query) -> new Reply(500, "{}");
        ApiException down = assertThrows(ApiException.class, () -> client("k").channel(new ChannelRef.Handle("x")));
        assertEquals("YOUTUBE_PROVIDER_ERROR", down.getCode());
        assertEquals(2, requests.size(), "one retry for a server error");
    }

    @Test
    void aMissingKeyMakesNoRequest() {
        ApiException e = assertThrows(ApiException.class, () -> client("  ").channel(new ChannelRef.Handle("x")));
        assertEquals("YOUTUBE_PROVIDER_ERROR", e.getCode());
        assertTrue(requests.isEmpty());
    }

    @Test
    void pagesThroughTheUploadsListAndBatchesVideoLookupsInFifties() {
        handler = (path, query) -> switch (path) {
            case "/youtube/v3/channels" -> new Reply(200, read("yt-channel.json"));
            case "/youtube/v3/playlistItems" -> {
                boolean second = query.contains("pageToken=");
                int from = second ? 50 : 0;
                int count = second ? 10 : 50;
                StringBuilder items = new StringBuilder();
                for (int i = from; i < from + count; i++) {
                    items.append(items.length() == 0 ? "" : ",")
                            .append("{\"contentDetails\":{\"videoId\":\"v").append(i).append("\"}}");
                }
                yield new Reply(200, "{\"nextPageToken\":\"" + (second ? "" : "NEXT") + "\",\"items\":[" + items + "]}"
                        .replace("\"nextPageToken\":\"\",", ""));
            }
            default -> {
                List<String> ids = new ArrayList<>(Arrays.asList(param("?" + query, "id").split(",")));
                Collections.shuffle(ids, new java.util.Random(7)); // the API does not promise order
                StringBuilder items = new StringBuilder();
                for (String id : ids) {
                    int n = Integer.parseInt(id.substring(1));
                    if (n == 13) {
                        continue; // a private or deleted video is simply missing from the answer
                    }
                    String day = String.format("2026-03-%02dT10:00:00Z", 30 - Math.min(n, 29));
                    items.append(items.length() == 0 ? "" : ",").append("{\"id\":\"").append(id)
                            .append("\",\"snippet\":{\"publishedAt\":\"").append(day).append("\",\"title\":\"T").append(n)
                            .append("\"},\"contentDetails\":{\"duration\":\"PT5M\"},\"statistics\":{\"viewCount\":\"1\"}}");
                }
                yield new Reply(200, "{\"items\":[" + items + "]}");
            }
        };
        YouTubeClient client = client("k");
        List<VideoInfo> v = client.recentVideos(uploadsOnly(client.channel(new ChannelRef.Handle("x"))), 60).videos();

        assertEquals(59, v.size(), "60 listed, one private video absent");
        assertFalse(v.stream().anyMatch(x -> x.videoId().equals("v13")));
        assertEquals(2, requests.stream().filter(r -> r.contains("/playlistItems")).count());
        List<String> videoCalls = requests.stream().filter(r -> r.contains("/videos?")).toList();
        assertEquals(2, videoCalls.size(), "60 ids need two videos.list calls");
        assertEquals(50, param(videoCalls.get(0), "id").split(",").length);
        assertEquals(10, param(videoCalls.get(1), "id").split(",").length);
        assertTrue(requests.stream().filter(r -> r.contains("/playlistItems")).allMatch(r -> param(r, "maxResults") != null));
        for (int i = 1; i < v.size(); i++) {
            assertFalse(v.get(i).publishedAt().isAfter(v.get(i - 1).publishedAt()), "sorted newest first");
        }
    }

    @Test
    void stopsAtTheRequestedNumberOfVideos() {
        YouTubeClient client = client("k");
        List<VideoInfo> v = client.recentVideos(uploadsOnly(client.channel(new ChannelRef.Handle("x"))), 3).videos();
        assertEquals(3, v.size());
        String listRequest = requests.stream().filter(r -> r.contains("/playlistItems")).findFirst().orElseThrow();
        assertEquals("3", param(listRequest, "maxResults"), "only 3 videos are requested, which keeps quota use minimal");
        assertEquals(1, requests.stream().filter(r -> r.contains("/playlistItems")).count(), "no extra page is fetched");
    }

    /** A channel whose id is not a UC id, so the Shorts and videos lists are skipped and the uploads list is used. */
    private static ChannelInfo uploadsOnly(ChannelInfo c) {
        return new ChannelInfo("legacy", c.title(), c.handle(), c.description(), c.country(), c.publishedAt(),
                c.subscribers(), c.subscribersHidden(), c.totalViews(), c.videoCount(), c.uploadsPlaylistId());
    }

    @Test
    void readsShortsAndVideosListsToTellFormatsApartForCertain() {
        handler = (path, query) -> switch (path) {
            case "/youtube/v3/channels" -> new Reply(200, read("yt-channel.json"));
            case "/youtube/v3/playlistItems" -> {
                String list = param("?" + query, "playlistId");
                if (list.startsWith("UUSH")) {
                    yield new Reply(200, "{\"pageInfo\":{\"totalResults\":7},\"items\":[{\"contentDetails\":"
                            + "{\"videoId\":\"s1\",\"videoPublishedAt\":\"2026-03-29T10:00:00Z\"}}]}");
                }
                yield new Reply(200, "{\"pageInfo\":{\"totalResults\":90},\"items\":[{\"contentDetails\":"
                        + "{\"videoId\":\"l1\",\"videoPublishedAt\":\"2026-03-28T10:00:00Z\"}}]}");
            }
            default -> {
                String ids = param("?" + query, "id");
                StringBuilder items = new StringBuilder();
                for (String id : ids.split(",")) {
                    items.append(items.length() == 0 ? "" : ",").append("{\"id\":\"").append(id)
                            .append("\",\"snippet\":{\"publishedAt\":\"")
                            .append(id.equals("s1") ? "2026-03-29T10:00:00Z" : "2026-03-28T10:00:00Z")
                            .append("\",\"title\":\"T").append(id).append("\"},\"contentDetails\":{\"duration\":\"PT")
                            .append(id.equals("s1") ? "45S" : "12M").append("\"},\"statistics\":{\"viewCount\":\"5\"}}");
                }
                yield new Reply(200, "{\"items\":[" + items + "]}");
            }
        };
        YouTubeClient client = client("k");
        YouTubeData.RecentVideos r = client.recentVideos(client.channel(new ChannelRef.Handle("x")), 10);

        assertTrue(r.formatsObserved());
        assertEquals(7L, r.shortsTotal());
        assertEquals(90L, r.longFormTotal());
        assertEquals(2, r.videos().size());
        assertEquals(Boolean.TRUE, r.videos().get(0).inShortsFeed());
        assertEquals(Boolean.FALSE, r.videos().get(1).inShortsFeed());
    }

    @Test
    void aChannelWithNoShortsListHasZeroShortsNotAnError() {
        handler = (path, query) -> switch (path) {
            case "/youtube/v3/channels" -> new Reply(200, read("yt-channel.json"));
            case "/youtube/v3/playlistItems" -> param("?" + query, "playlistId").startsWith("UUSH")
                    ? new Reply(404, "{\"error\":{\"code\":404,\"errors\":[{\"reason\":\"playlistNotFound\"}]}}")
                    : new Reply(200, "{\"pageInfo\":{\"totalResults\":1},\"items\":[{\"contentDetails\":"
                            + "{\"videoId\":\"l1\",\"videoPublishedAt\":\"2026-03-28T10:00:00Z\"}}]}");
            default -> new Reply(200, "{\"items\":[{\"id\":\"l1\",\"snippet\":{\"publishedAt\":"
                    + "\"2026-03-28T10:00:00Z\",\"title\":\"T\"},\"contentDetails\":{\"duration\":\"PT9M\"},"
                    + "\"statistics\":{\"viewCount\":\"5\"}}]}");
        };
        YouTubeClient client = client("k");
        YouTubeData.RecentVideos r = client.recentVideos(client.channel(new ChannelRef.Handle("x")), 10);
        assertTrue(r.formatsObserved());
        assertEquals(0L, r.shortsTotal());
        assertEquals(1, r.videos().size());
    }
}
