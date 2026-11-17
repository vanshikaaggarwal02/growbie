package com.launchmate.website;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Google PageSpeed Insights API v5, mobile. Only the fields the audit needs are kept (the full
 * response is about 400 KB). Every failure becomes an "unavailable" result with a reason, never an
 * exception, so a PageSpeed outage cannot discard the crawl. The API key is in the request URL, so
 * the URL is never logged.
 */
public class PageSpeedClient {

    /** Scores are 0 to 100; a category the API did not return is simply absent from the map. */
    public record Result(boolean available, String strategy, Map<String, Integer> scores,
                         Map<String, String> metrics, List<String> issues, String fieldDataCategory,
                         String measuredAt, String error) {

        static Result unavailable(String reason) {
            return new Result(false, "mobile", Map.of(), Map.of(), List.of(), null, null, reason);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PageSpeedClient.class);
    private static final Map<String, String> CATEGORY_KEYS = Map.of(
            "performance", "performance", "accessibility", "accessibility",
            "best-practices", "bestPractices", "seo", "seo");
    private static final Map<String, String> METRIC_KEYS = Map.of(
            "first-contentful-paint", "firstContentfulPaint",
            "largest-contentful-paint", "largestContentfulPaint",
            "total-blocking-time", "totalBlockingTime",
            "cumulative-layout-shift", "cumulativeLayoutShift",
            "speed-index", "speedIndex");
    private static final int MAX_ISSUES = 8;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper json;

    public PageSpeedClient(String baseUrl, String apiKey, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.json = json;
    }

    public Result analyse(String pageUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.unavailable("PageSpeed is not configured.");
        }
        String url = baseUrl + "/pagespeedonline/v5/runPagespeed?url=" + enc(pageUrl) + "&strategy=mobile"
                + "&category=performance&category=accessibility&category=best-practices&category=seo"
                + "&key=" + enc(apiKey);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(90)).header("Accept", "application/json").GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return parse(response.body());
                }
                log.warn("PageSpeed request failed with status {} (attempt {})", status, attempt);
                if (status == 429 || status == 403) {
                    return Result.unavailable("The PageSpeed request limit was reached. Try again later.");
                }
                if (status < 500) {
                    return Result.unavailable("PageSpeed could not analyse this page.");
                }
            } catch (IOException e) {
                log.warn("PageSpeed request failed: {} (attempt {})", e.getClass().getSimpleName(), attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.unavailable("PageSpeed was interrupted.");
            }
            if (attempt == 1) {
                pause(1500);
            }
        }
        return Result.unavailable("PageSpeed did not respond.");
    }

    Result parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (RuntimeException e) {
            return Result.unavailable("PageSpeed returned an unreadable response.");
        }
        JsonNode lh = root.path("lighthouseResult");
        if (!lh.isObject()) {
            return Result.unavailable("PageSpeed returned no results for this page.");
        }

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : CATEGORY_KEYS.entrySet()) {
            JsonNode score = lh.path("categories").path(e.getKey()).path("score");
            if (score.isNumber()) {
                scores.put(e.getValue(), (int) Math.round(score.asDouble() * 100));
            }
        }
        if (scores.isEmpty()) {
            return Result.unavailable("PageSpeed returned no scores for this page.");
        }

        Map<String, String> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : METRIC_KEYS.entrySet()) {
            JsonNode display = lh.path("audits").path(e.getKey()).get("displayValue");
            if (display != null && display.isString()) {
                metrics.put(e.getValue(), PageExtractor.collapse(display.asString()));
            }
        }

        record Issue(double score, String title) {}
        List<Issue> failing = new ArrayList<>();
        for (JsonNode audit : lh.path("audits")) {
            String mode = audit.path("scoreDisplayMode").isString() ? audit.path("scoreDisplayMode").asString() : "";
            JsonNode score = audit.path("score");
            JsonNode title = audit.get("title");
            if (score.isNumber() && score.asDouble() < 0.9 && title != null && title.isString()
                    && (mode.equals("binary") || mode.equals("numeric"))) {
                failing.add(new Issue(score.asDouble(), PageExtractor.collapse(title.asString())));
            }
        }
        failing.sort(Comparator.comparingDouble(Issue::score));
        List<String> issues = failing.stream().limit(MAX_ISSUES).map(Issue::title).toList();

        JsonNode field = root.path("loadingExperience").get("overall_category");
        JsonNode fetchTime = lh.get("fetchTime");
        return new Result(true, "mobile", Map.copyOf(scores), Map.copyOf(metrics), issues,
                field != null && field.isString() ? field.asString() : null,
                fetchTime != null && fetchTime.isString() ? fetchTime.asString() : null, null);
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
