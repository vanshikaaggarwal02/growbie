package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Crawls a fake site on localhost and checks both what was fetched and what was never requested. */
class WebsiteCrawlerTest {

    private static final long SMALL_LIMIT = 3000;

    private HttpServer server;
    private String base;
    private final List<String> hits = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> robots = new AtomicReference<>("User-agent: *\nDisallow: /private\n");
    private SafeFetcher permissive;

    private static String page(String title, String body) {
        return "<html><head><title>" + title + "</title></head><body><h1>" + title + "</h1>" + body + "</body></html>";
    }

    private void respond(HttpExchange ex, int status, String type, String body, String location) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        if (location != null) {
            ex.getResponseHeaders().add("Location", location);
        }
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            hits.add(path);
            switch (path) {
                case "/robots.txt" -> respond(ex, 200, "text/plain", robots.get(), null);
                case "/" -> respond(ex, 200, "text/html", page("Home", """
                        <a href="/about">About</a> <a href="/contact">Contact</a> <a href="/private/secret">Secret</a>
                        <a href="/file.pdf">Brochure</a> <a href="/old">Old</a> <a href="/out">Out</a>
                        <a href="/text">Text</a> <a href="/big">Big</a> <a href="https://other.example.net/page">Other</a>
                        <a href="mailto:a@b.co">Mail</a> <a href="#top">Top</a>"""), null);
                case "/about" -> respond(ex, 200, "text/html", page("About", """
                        <a href="/">Home</a> <a href="/about/team">Team</a>"""), null);
                case "/about/team" -> respond(ex, 200, "text/html", page("Team", "<p>The team.</p>"), null);
                case "/old" -> respond(ex, 301, "text/html", "", "/about");
                case "/out" -> respond(ex, 302, "text/html", "", "http://elsewhere.example.net/");
                case "/redir-internal" -> respond(ex, 302, "text/html", "", "http://169.254.169.254/latest/meta-data/");
                case "/text" -> respond(ex, 200, "text/plain", "just text", null);
                case "/big" -> respond(ex, 200, "text/html",
                        page("Big", "<p>" + "word ".repeat(2000) + "</p>"), null);
                case "/private/secret" -> respond(ex, 200, "text/html", page("Secret", "x"), null);
                default -> respond(ex, 404, "text/html", page("Not found", ""), null);
            }
        });
        server.start();
        permissive = new SafeFetcher(new UrlGuard(InetAddress::getAllByName, a -> true), "LaunchMateBot-test",
                Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() throws Exception {
        server.stop(0);
        permissive.close();
    }

    private WebsiteCrawler crawler() {
        return new WebsiteCrawler(permissive, url -> URI.create(url));
    }

    private static WebsiteCrawler.Options options(int maxPages, int maxDepth) {
        return new WebsiteCrawler.Options(maxPages, maxDepth, SMALL_LIMIT, Duration.ofSeconds(20), 0);
    }

    private static List<String> paths(WebsiteCrawler.Result r) {
        return r.pages().stream().map(p -> URI.create(p.url()).getPath()).toList();
    }

    @Test
    void auditsSameSiteHtmlPagesAndSkipsEverythingElse() {
        WebsiteCrawler.Result r = crawler().crawl(URI.create(base + "/"), options(10, 2));

        List<String> paths = paths(r);
        assertTrue(paths.containsAll(List.of("/", "/about", "/about/team", "/big")), paths.toString());
        assertEquals(1, paths.stream().filter("/about"::equals).count(),
                "the redirect from /old to /about must not produce a duplicate page");
        for (String notAudited : List.of("/contact", "/private/secret", "/text", "/out", "/file.pdf", "/old")) {
            assertFalse(paths.contains(notAudited), notAudited + " must not be audited");
        }
        assertTrue(r.robotsFound());
    }

    @Test
    void neverRequestsDisallowedAssetOrOffSiteAddresses() {
        crawler().crawl(URI.create(base + "/"), options(10, 2));

        assertFalse(hits.contains("/private/secret"), "robots.txt disallows it, so it must never be requested");
        assertFalse(hits.contains("/file.pdf"), "asset files are never fetched");
        assertFalse(hits.stream().anyMatch(h -> h.contains("other.example.net") || h.contains("elsewhere")),
                "off-site addresses must never be requested");
    }

    @Test
    void reportsWhatWasSkippedAndTruncated() {
        WebsiteCrawler.Result r = crawler().crawl(URI.create(base + "/"), options(10, 2));
        String warnings = String.join("\n", r.warnings());

        assertTrue(warnings.contains("HTTP 404"), warnings);
        assertTrue(warnings.contains("robots.txt does not allow"), warnings);
        assertTrue(r.skipped().stream().anyMatch(x -> x.reason().equals("ROBOTS_DISALLOWED") && x.url().contains("/private")),
                "the skipped page is listed with its address and reason: " + r.skipped());
        assertTrue(r.skipped().stream().anyMatch(x -> x.reason().equals("HTTP_ERROR")), r.skipped().toString());
        assertTrue(warnings.contains("larger than the size limit"), warnings);
        assertTrue(warnings.contains("redirects to another website"), warnings);
        PageData big = r.pages().stream().filter(p -> p.url().endsWith("/big")).findFirst().orElseThrow();
        assertTrue(big.truncated());
        assertTrue(big.bytes() <= SMALL_LIMIT);
    }

    @Test
    void respectsThePageAndDepthLimits() {
        assertEquals(2, crawler().crawl(URI.create(base + "/"), options(2, 2)).pages().size());

        List<String> shallow = paths(crawler().crawl(URI.create(base + "/"), options(10, 1)));
        assertTrue(shallow.contains("/about"));
        assertFalse(shallow.contains("/about/team"), "a page two links deep is beyond a depth limit of 1");
    }

    @Test
    void stopsWhenRobotsDisallowsTheHomepage() {
        robots.set("User-agent: *\nDisallow: /\n");
        CrawlFailedException e = assertThrows(CrawlFailedException.class,
                () -> crawler().crawl(URI.create(base + "/"), options(10, 2)));
        assertTrue(e.getMessage().contains("robots.txt"), e.getMessage());
        assertEquals(List.of("/", "/robots.txt"), hits, "only the homepage and robots.txt are requested");
    }

    @Test
    void stopsWhenTheHomepageIsAnError() {
        CrawlFailedException e = assertThrows(CrawlFailedException.class,
                () -> crawler().crawl(URI.create(base + "/nothing-here"), options(10, 2)));
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
    }

    @Test
    void connectionTimeGuardBlocksLoopbackEvenWithoutAnAddressCheck() throws Exception {
        try (SafeFetcher strict = new SafeFetcher(UrlGuard.strict(), "LaunchMateBot-test",
                Duration.ofSeconds(3), Duration.ofSeconds(5))) {
            WebsiteCrawler crawler = new WebsiteCrawler(strict, url -> URI.create(url));
            CrawlFailedException e = assertThrows(CrawlFailedException.class,
                    () -> crawler.crawl(URI.create("http://localhost:" + server.getAddress().getPort() + "/"),
                            options(10, 2)));
            assertTrue(e.getMessage().contains("private or internal"), e.getMessage());
            assertTrue(hits.isEmpty(), "the loopback server must never have been contacted");
        }
    }

    @Test
    void aRedirectToACloudMetadataAddressIsRefused() {
        UrlGuard strictSyntax = UrlGuard.strict();
        WebsiteCrawler crawler = new WebsiteCrawler(permissive, strictSyntax::validateSyntax);
        CrawlFailedException e = assertThrows(CrawlFailedException.class,
                () -> crawler.crawl(URI.create(base + "/redir-internal"), options(10, 2)));
        assertTrue(e.getMessage().contains("cannot be audited"), e.getMessage());
        assertEquals(List.of("/redir-internal"), hits, "the metadata address must never be requested");
    }
}
