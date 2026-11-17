package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Runs the client against a fake server that replays a trimmed copy of a real PageSpeed response. */
class PageSpeedClientTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>();
    private final List<String> queries = new CopyOnWriteArrayList<>();

    private static String fixture() throws Exception {
        try (InputStream in = PageSpeedClientTest.class.getResourceAsStream("/psi-sample.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void start() throws Exception {
        body.set(fixture());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", ex -> {
            queries.add(ex.getRequestURI().getPath() + "?" + ex.getRequestURI().getRawQuery());
            byte[] out = body.get().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PageSpeedClient client(String key) {
        return new PageSpeedClient(base, key, JsonMapper.builder().build());
    }

    @Test
    void parsesScoresMetricsIssuesAndFieldDataFromARealResponse() {
        PageSpeedClient.Result r = client("test-key").analyse("https://example.com/");

        assertTrue(r.available());
        assertEquals("mobile", r.strategy());
        assertEquals(100, r.scores().get("performance"));
        assertEquals(96, r.scores().get("accessibility"));
        assertEquals(100, r.scores().get("bestPractices"));
        assertEquals(80, r.scores().get("seo"));
        // The real API writes "0.8" and "s" with a non-breaking space; it must come out as a plain space.
        assertEquals("0.8 s", r.metrics().get("firstContentfulPaint"));
        assertFalse(r.metrics().values().stream().anyMatch(v -> v.indexOf(' ') >= 0));
        assertEquals("0.8 s", r.metrics().get("largestContentfulPaint"));
        assertEquals("0 ms", r.metrics().get("totalBlockingTime"));
        assertEquals("FAST", r.fieldDataCategory());
        assertTrue(r.measuredAt().startsWith("2026-"));
        assertTrue(r.issues().contains("Document does not have a meta description"), r.issues().toString());
        assertTrue(r.issues().contains("Links do not have descriptive text"), r.issues().toString());
        assertFalse(r.issues().stream().anyMatch(t -> t.toLowerCase().contains("structured data")),
                "manual and not-applicable audits are not failures");
    }

    @Test
    void sendsAMobileRequestForAllFourCategoriesWithTheKey() {
        client("secret-key").analyse("https://example.com/path?a=1");
        String q = queries.get(0);
        assertTrue(q.startsWith("/pagespeedonline/v5/runPagespeed?"), q);
        assertTrue(q.contains("strategy=mobile"));
        for (String c : List.of("performance", "accessibility", "best-practices", "seo")) {
            assertTrue(q.contains("category=" + c), c);
        }
        assertTrue(q.contains("key=secret-key"));
        assertTrue(q.contains("url=https%3A%2F%2Fexample.com%2Fpath%3Fa%3D1"), "the audited URL is encoded");
    }

    @Test
    void aMissingKeyMakesNoRequestAndSaysSo() {
        PageSpeedClient.Result r = client("  ").analyse("https://example.com/");
        assertFalse(r.available());
        assertTrue(r.error().contains("not configured"));
        assertTrue(queries.isEmpty());
    }

    @Test
    void quotaAndClientErrorsAreReportedWithoutRetrying() {
        status.set(429);
        PageSpeedClient.Result quota = client("k").analyse("https://example.com/");
        assertFalse(quota.available());
        assertTrue(quota.error().contains("limit"), quota.error());
        assertEquals(1, queries.size(), "a quota error is not retried");

        queries.clear();
        status.set(400);
        PageSpeedClient.Result bad = client("k").analyse("https://example.com/");
        assertTrue(bad.error().contains("could not analyse"), bad.error());
        assertEquals(1, queries.size());
    }

    @Test
    void serverErrorsAreRetriedOnceThenReported() {
        status.set(500);
        PageSpeedClient.Result r = client("k").analyse("https://example.com/");
        assertFalse(r.available());
        assertEquals(2, queries.size(), "one retry for a server error");
        assertTrue(r.error().contains("did not respond"));
    }

    @Test
    void unreadableOrEmptyResponsesAreUnavailableNotErrors() {
        body.set("<html>not json</html>");
        assertFalse(client("k").analyse("https://example.com/").available());

        body.set("{\"lighthouseResult\":{\"categories\":{}}}");
        PageSpeedClient.Result noScores = client("k").analyse("https://example.com/");
        assertFalse(noScores.available());
        assertTrue(noScores.error().contains("no scores"));

        body.set("{}");
        assertFalse(client("k").analyse("https://example.com/").available());
    }

    @Test
    void aCategoryThePageSpeedApiDidNotReturnIsAbsentNeverZero() {
        body.set("{\"lighthouseResult\":{\"categories\":{\"performance\":{\"score\":0.5}},\"audits\":{}}}");
        PageSpeedClient.Result r = client("k").analyse("https://example.com/");
        assertTrue(r.available());
        assertEquals(50, r.scores().get("performance"));
        assertNull(r.scores().get("seo"));
        assertNull(r.fieldDataCategory());
        assertEquals(List.of(), r.issues());
    }
}
