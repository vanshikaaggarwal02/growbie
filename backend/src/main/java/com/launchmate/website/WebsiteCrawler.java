package com.launchmate.website;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small, polite crawler for auditing one site. It stays on the site's own host, obeys robots.txt,
 * fetches a bounded number of HTML pages, and never follows a redirect to another site. Address
 * safety is enforced by the {@link SafeFetcher} at connection time and by the {@link UrlChecker}
 * on every address before it is fetched.
 */
public final class WebsiteCrawler {

    /** Syntax-level address check applied to every URL before it is fetched. */
    @FunctionalInterface
    public interface UrlChecker {
        URI check(String url);
    }

    public record Options(int maxPages, int maxDepth, long maxBytesPerPage, Duration totalTimeout,
                          long politeDelayMillis) {
        public static Options of(int maxPages) {
            return new Options(Math.max(1, Math.min(maxPages, 10)), 2, 1_000_000L, Duration.ofSeconds(60), 250);
        }
    }

    /** A page the crawler found a link to but did not audit, and why. {@code reason} is a stable code. */
    public record Skipped(String url, String reason, String detail) {}

    /**
     * {@code skipped} lists pages found but not audited, with the reason. {@code notVisited} counts further
     * links that were queued but never reached because the page or time limit was hit first.
     */
    public record Result(String host, URI startUri, List<PageData> pages, List<String> warnings,
                         boolean robotsFound, List<Skipped> skipped, int notVisited,
                         List<String> aiCrawlersBlocked, Integer sitemapEntries) {
        public Result(String host, URI startUri, List<PageData> pages, List<String> warnings, boolean robotsFound) {
            this(host, startUri, pages, warnings, robotsFound, List.of(), 0, List.of(), null);
        }
    }

    /** Well-known AI crawlers. robots.txt rules naming these decide whether AI tools may read a site. */
    static final List<String> AI_CRAWLERS = List.of("GPTBot", "ClaudeBot", "PerplexityBot", "Google-Extended", "CCBot");

    private record Fetched(SafeFetcher.Response response, URI finalUri) {}

    private record Candidate(String url, int depth) {}

    private static final String AGENT_TOKEN = "growbiebot";
    private static final int MAX_REDIRECTS = 3;
    private static final Set<String> ASSET_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "css", "js", "json", "xml", "pdf", "zip", "gz",
            "mp3", "mp4", "mov", "avi", "woff", "woff2", "ttf", "doc", "docx", "xls", "xlsx", "ppt", "pptx");

    private final SafeFetcher fetcher;
    private final UrlChecker checker;

    public WebsiteCrawler(SafeFetcher fetcher, UrlChecker checker) {
        this.fetcher = fetcher;
        this.checker = checker;
    }

    public Result crawl(URI start, Options options) {
        long deadline = System.nanoTime() + options.totalTimeout().toNanos();
        List<String> warnings = new ArrayList<>();

        // The homepage may redirect to another host (for example to www); that host becomes the site.
        Fetched home = fetchFollowing(start, null, options, warnings, true);
        SafeFetcher.Response homeResponse = home.response();
        if (homeResponse.status() >= 400) {
            throw new CrawlFailedException("The homepage returned HTTP " + homeResponse.status()
                    + ", so the site could not be audited.");
        }
        if (!isHtml(homeResponse)) {
            throw new CrawlFailedException("The address did not return a web page (HTML).");
        }
        URI homeUri = home.finalUri();
        String siteKey = PageExtractor.siteKey(homeUri.getHost());

        boolean robotsFound = false;
        RobotsTxt robots = RobotsTxt.ALLOW_ALL;
        List<String> aiBlocked = new ArrayList<>();
        try {
            SafeFetcher.Response r = fetcher.get(homeUri.resolve("/robots.txt"), 512_000);
            if (r.status() == 200) {
                robotsFound = true;
                String robotsText = new String(r.body(), StandardCharsets.UTF_8);
                robots = RobotsTxt.parse(robotsText, AGENT_TOKEN);
                for (String bot : AI_CRAWLERS) {
                    if (!RobotsTxt.parse(robotsText, bot).allows("/")) {
                        aiBlocked.add(bot);
                    }
                }
            } else if (r.status() >= 500) {
                warnings.add("The site's robots.txt could not be read (HTTP " + r.status()
                        + "), so only the homepage was audited.");
                robots = RobotsTxt.parse("User-agent: *\nDisallow: /", AGENT_TOKEN);
            }
        } catch (IOException e) {
            warnings.add("The site's robots.txt could not be fetched, so it could not be checked.");
        }
        if (!robots.allows(pathOf(homeUri))) {
            throw new CrawlFailedException(
                    "The site's robots.txt does not allow automated audits of the homepage, so nothing was fetched.");
        }

        // One small request: does the site publish a sitemap? null means it could not be checked.
        Integer sitemapEntries = null;
        try {
            SafeFetcher.Response r = fetcher.get(homeUri.resolve("/sitemap.xml"), 1_000_000);
            if (r.status() == 404 || r.status() == 410) {
                sitemapEntries = 0;
            } else if (r.status() == 200) {
                String body = new String(r.body(), StandardCharsets.UTF_8);
                if (body.contains("<urlset") || body.contains("<sitemapindex")) {
                    int count = 0;
                    for (int i = body.indexOf("<loc>"); i >= 0; i = body.indexOf("<loc>", i + 5)) {
                        count++;
                    }
                    sitemapEntries = count;
                } else {
                    sitemapEntries = 0;
                }
            }
        } catch (IOException ignored) {
            // leave as null: not checked
        }

        List<PageData> pages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        PageData homePage = PageExtractor.extract(homeResponse, homeUri, siteKey);
        pages.add(homePage);
        seen.add(key(homeUri));
        warnTruncated(homePage, warnings);

        Deque<Candidate> queue = new ArrayDeque<>();
        Set<String> queued = new HashSet<>();
        enqueue(homePage, 1, options, queue, queued, seen);

        int robotsSkipped = 0;
        List<Skipped> skipped = new ArrayList<>();
        while (pages.size() < options.maxPages() && !queue.isEmpty()) {
            if (System.nanoTime() > deadline) {
                warnings.add("The crawl time limit was reached; some pages were not audited.");
                break;
            }
            Candidate c = queue.poll();
            URI uri;
            try {
                uri = checker.check(c.url());
            } catch (UrlRejectedException e) {
                continue;
            }
            if (!PageExtractor.isSameSite(uri.toString(), siteKey) || isAsset(uri) || seen.contains(key(uri))) {
                continue;
            }
            if (!robots.allows(pathOf(uri))) {
                robotsSkipped++;
                skipped.add(new Skipped(uri.toString(), "ROBOTS_DISALLOWED",
                        "This site's robots.txt asks automated tools not to fetch this page, so it was left out."));
                continue;
            }
            pause(options.politeDelayMillis());
            Fetched f;
            try {
                f = fetchFollowing(uri, siteKey, options, warnings, false);
            } catch (CrawlFailedException e) {
                warnings.add(e.getMessage());
                skipped.add(new Skipped(uri.toString(), "FETCH_FAILED", e.getMessage()));
                continue;
            }
            if (!seen.add(key(f.finalUri()))) {
                continue; // a redirect landed on a page that was already audited
            }
            SafeFetcher.Response r = f.response();
            if (r.status() >= 400) {
                warnings.add("Skipped " + f.finalUri() + " (HTTP " + r.status() + ").");
                skipped.add(new Skipped(f.finalUri().toString(), "HTTP_ERROR",
                        "The page answered with an error (HTTP " + r.status() + "), so there was nothing to audit."));
                continue;
            }
            if (!isHtml(r)) {
                continue;
            }
            PageData page = PageExtractor.extract(r, f.finalUri(), siteKey);
            pages.add(page);
            warnTruncated(page, warnings);
            if (c.depth() < options.maxDepth()) {
                enqueue(page, c.depth() + 1, options, queue, queued, seen);
            }
        }
        if (robotsSkipped > 0) {
            warnings.add(robotsSkipped + " page(s) were skipped because robots.txt does not allow them.");
        }
        return new Result(siteKey, homeUri, List.copyOf(pages), List.copyOf(warnings), robotsFound,
                List.copyOf(skipped), queue.size(), List.copyOf(aiBlocked), sitemapEntries);
    }

    /**
     * Fetches one address, following up to three redirects. For the homepage, any failure aborts the
     * crawl; for other pages a failure is reported to the caller as a {@link CrawlFailedException}.
     */
    private Fetched fetchFollowing(URI uri, String siteKey, Options options, List<String> warnings,
                                   boolean homepage) {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            SafeFetcher.Response r;
            try {
                r = fetcher.get(current, options.maxBytesPerPage());
            } catch (UnknownHostException e) {
                throw new CrawlFailedException(e.getMessage() != null && e.getMessage().startsWith("blocked")
                        ? "This address points to a private or internal network and was not fetched."
                        : "The website address could not be found.");
            } catch (IOException e) {
                throw new CrawlFailedException(homepage
                        ? "The website could not be reached."
                        : "Could not fetch " + current + ".");
            }
            boolean redirect = r.status() == 301 || r.status() == 302 || r.status() == 303
                    || r.status() == 307 || r.status() == 308;
            if (!redirect || r.location() == null) {
                return new Fetched(r, current);
            }
            URI next;
            try {
                next = checker.check(current.resolve(r.location()).toString());
            } catch (UrlRejectedException | IllegalArgumentException e) {
                throw new CrawlFailedException(homepage
                        ? "The website redirects to an address that cannot be audited."
                        : "Skipped " + current + " (it redirects to an address that cannot be audited).");
            }
            if (siteKey != null && !PageExtractor.isSameSite(next.toString(), siteKey)) {
                throw new CrawlFailedException("Skipped " + current + " (it redirects to another website).");
            }
            current = next;
        }
        throw new CrawlFailedException(homepage
                ? "The website redirects too many times."
                : "Skipped " + uri + " (too many redirects).");
    }

    private void enqueue(PageData page, int depth, Options options, Deque<Candidate> queue,
                         Set<String> queued, Set<String> seen) {
        if (depth > options.maxDepth()) {
            return;
        }
        for (String link : page.links()) {
            URI uri;
            try {
                uri = URI.create(link);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String k = key(uri);
            if (!seen.contains(k) && queued.add(k)) {
                queue.add(new Candidate(link, depth));
            }
        }
    }

    private static void warnTruncated(PageData page, List<String> warnings) {
        if (page.truncated()) {
            warnings.add(page.url() + " is larger than the size limit; only its first part was audited.");
        }
    }

    private static boolean isHtml(SafeFetcher.Response r) {
        String ct = r.contentType() == null ? "" : r.contentType().toLowerCase(Locale.ROOT);
        return ct.contains("text/html") || ct.contains("application/xhtml");
    }

    private static boolean isAsset(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') && ASSET_EXTENSIONS.contains(path.substring(dot + 1));
    }

    private static String pathOf(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    /** Identity of a page: host without www, path without a trailing slash, and the query. */
    private static String key(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return PageExtractor.siteKey(uri.getHost()) + (path.isEmpty() ? "/" : path) + query;
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
