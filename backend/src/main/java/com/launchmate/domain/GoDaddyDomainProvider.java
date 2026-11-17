package com.launchmate.domain;

import com.launchmate.common.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GoDaddy Domains API v3, read-only. Verified with real calls:
 * <ul>
 *   <li>POST /v3/domains/check-availability checks up to 25 domains per request and returns items[];
 *       an item that could not be checked carries an error object instead of availability.</li>
 *   <li>optimizeFor=ACCURACY gives definitive answers where the default SPEED does not.</li>
 *   <li>GET /v3/domains/suggestions returns only domains that are available.</li>
 * </ul>
 * The token is sent only in the Authorization header and is never logged.
 */
public class GoDaddyDomainProvider implements DomainProvider {

    private static final Logger log = LoggerFactory.getLogger(GoDaddyDomainProvider.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final int BATCH_SIZE = 25;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // Count every call, so all users together stay under the provider's per-minute limit.
    private final RateWindow window = new RateWindow(50, Duration.ofMinutes(1));
    private final String baseUrl;
    private final String pat;
    private final ObjectMapper json;

    public GoDaddyDomainProvider(String baseUrl, String pat, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.pat = pat;
        this.json = json;
    }

    @Override
    public String name() {
        return "GODADDY";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public List<ProviderAvailability> checkAll(List<String> domains) {
        List<ProviderAvailability> out = new ArrayList<>();
        for (int i = 0; i < domains.size(); i += BATCH_SIZE) {
            List<String> chunk = domains.subList(i, Math.min(i + BATCH_SIZE, domains.size()));
            String body = json.writeValueAsString(Map.of("domains", chunk, "optimizeFor", "ACCURACY"));
            JsonNode response = send("POST", "/v3/domains/check-availability", body);

            Map<String, JsonNode> byDomain = new HashMap<>();
            for (JsonNode item : response.path("items")) {
                String domain = text(item, "domain");
                if (domain != null) {
                    byDomain.put(domain, item);
                }
            }
            for (String domain : chunk) {
                out.add(parse(domain, byDomain.get(domain)));
            }
        }
        return out;
    }

    @Override
    public List<ProviderSuggestion> suggest(String keyword, List<String> tlds, int limit) {
        String path = "/v3/domains/suggestions?query=" + enc(keyword)
                + (tlds == null || tlds.isEmpty() ? "" : "&tlds=" + String.join(",", tlds))
                + "&pageSize=" + limit;
        JsonNode response = send("GET", path, null);
        List<ProviderSuggestion> out = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            String domain = text(item, "domain");
            if (domain != null) {
                out.add(new ProviderSuggestion(domain, text(item, "inventory"), price(item.path("prices"))));
            }
        }
        return out;
    }

    private static ProviderAvailability parse(String domain, JsonNode item) {
        if (item == null) {
            return new ProviderAvailability(domain, Availability.UNKNOWN, false, null, null,
                    "The lookup did not return a result for this name.");
        }
        JsonNode error = item.get("error");
        if (error != null && !error.isNull()) {
            String message = text(error, "message");
            return new ProviderAvailability(domain, Availability.UNKNOWN, false, null, null,
                    message == null ? "The provider could not check this name." : message);
        }
        boolean available = item.path("available").asBoolean(false);
        boolean definitive = item.path("definitive").asBoolean(false);
        Availability availability = !definitive ? Availability.UNKNOWN
                : available ? Availability.AVAILABLE : Availability.UNAVAILABLE;
        return new ProviderAvailability(domain, availability, definitive, text(item, "inventory"),
                price(item.path("prices")), null);
    }

    private JsonNode send(String method, String pathAndQuery, String body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (!window.tryAcquire()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DOMAIN_CHECK_UNAVAILABLE",
                        "Domain lookups are busy. Please try again in a minute.");
            }
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                        .timeout(Duration.ofSeconds(25))
                        .header("Authorization", "Bearer " + pat)
                        .header("Accept", "application/json");
                if (body == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(body));
                }
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return json.readTree(response.body());
                }
                boolean transientFailure = status == 429 || status >= 500;
                log.warn("GoDaddy request failed with status {} (attempt {})", status, attempt);
                if (!transientFailure || attempt == MAX_ATTEMPTS) {
                    throw providerError();
                }
                pause(400L * attempt);
            } catch (IOException e) {
                log.warn("GoDaddy request failed: {} (attempt {})", e.getClass().getSimpleName(), attempt);
                if (attempt == MAX_ATTEMPTS) {
                    throw providerError();
                }
                pause(400L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw providerError();
            }
        }
        throw providerError();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ApiException providerError() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "GODADDY_PROVIDER_ERROR",
                "The domain lookup service did not respond. Please try again.");
    }

    /** Prefers the one-year price; sums every listed fee (for example the premium purchase fee). */
    private static ProviderPrice price(JsonNode prices) {
        JsonNode chosen = null;
        for (JsonNode p : prices) {
            if (chosen == null) {
                chosen = p;
            }
            if (p.path("period").asInt(0) == 1) {
                chosen = p;
                break;
            }
        }
        if (chosen == null || !chosen.path("price").has("value")) {
            return null;
        }
        JsonNode priceNode = chosen.path("price");
        JsonNode renewalNode = chosen.path("renewalPrice");
        Long renewal = renewalNode.has("value") ? renewalNode.path("value").asLong() : null;
        long feeTotal = 0;
        boolean anyFee = false;
        for (JsonNode f : chosen.path("fees")) {
            if (f.path("fee").has("value")) {
                feeTotal += f.path("fee").path("value").asLong();
                anyFee = true;
            }
        }
        return new ProviderPrice(priceNode.path("value").asLong(), text(priceNode, "currencyCode"),
                chosen.path("period").asInt(1), renewal, anyFee ? feeTotal : null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
