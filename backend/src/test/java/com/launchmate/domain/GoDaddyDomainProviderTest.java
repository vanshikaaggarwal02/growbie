package com.launchmate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.domain.DomainProvider.ProviderAvailability;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Runs the provider against a local fake server that replays response shapes captured from GoDaddy. */
class GoDaddyDomainProviderTest {

    private static final String BULK_RESPONSE = """
            {"items":[
              {"available":true,"definitive":true,"domain":"barebotanics.com","inventory":"PREMIUM",
               "prices":[{"fees":[{"fee":{"currencyCode":"USD","value":248800},"type":"ONE_TIME_PREMIUM_DOMAIN_PURCHASE"}],
                          "period":1,"price":{"currencyCode":"USD","value":2299},"term":"YEAR"}]},
              {"available":true,"definitive":true,"domain":"barebotanics.in","inventory":"REGISTRY",
               "prices":[{"period":2,"price":{"currencyCode":"USD","value":2598},"term":"YEAR"},
                         {"period":1,"price":{"currencyCode":"USD","value":1299},"recommended":true,
                          "renewalPrice":{"currencyCode":"USD","value":1299},"term":"YEAR"}]},
              {"available":false,"definitive":true,"domain":"taken.com","inventory":"REGISTRY"},
              {"available":true,"definitive":false,"domain":"maybe.in","inventory":"REGISTRY"},
              {"domain":"bad_domain","error":{"message":"invalid domain name: bad_domain","name":"INVALID_DOMAIN"}}
            ]}""";

    private HttpServer server;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastMethodAndPath = new AtomicReference<>();
    private GoDaddyDomainProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastMethodAndPath.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            byte[] out = BULK_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        provider = new GoDaddyDomainProvider("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-token", JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsOneBulkRequestWithAccuracyAndBearerToken() {
        provider.checkAll(List.of("barebotanics.com", "barebotanics.in"));
        assertEquals("POST /v3/domains/check-availability", lastMethodAndPath.get());
        assertEquals("Bearer test-token", lastAuth.get());
        assertTrue(lastBody.get().contains("\"optimizeFor\":\"ACCURACY\""), lastBody.get());
        assertTrue(lastBody.get().contains("barebotanics.com"));
    }

    @Test
    void parsesPremiumFeeAndPrefersTheOneYearPrice() {
        List<ProviderAvailability> r = provider.checkAll(
                List.of("barebotanics.com", "barebotanics.in", "taken.com", "maybe.in", "bad_domain"));

        ProviderAvailability premium = r.get(0);
        assertEquals(Availability.AVAILABLE, premium.availability());
        assertEquals("PREMIUM", premium.inventory());
        assertEquals(2299L, premium.price().value());
        assertEquals("USD", premium.price().currency());
        assertEquals(248800L, premium.price().premiumFee());

        ProviderAvailability regular = r.get(1);
        assertEquals(1299L, regular.price().value(), "the 1-year price must win over the 2-year entry");
        assertEquals(1, regular.price().period());
        assertEquals(1299L, regular.price().renewal());
        assertNull(regular.price().premiumFee());
    }

    @Test
    void neverTurnsUncertainAnswersIntoAvailableOrUnavailable() {
        List<ProviderAvailability> r = provider.checkAll(
                List.of("barebotanics.com", "barebotanics.in", "taken.com", "maybe.in", "bad_domain"));

        assertEquals(Availability.UNAVAILABLE, r.get(2).availability());
        assertNull(r.get(2).price());

        assertEquals(Availability.UNKNOWN, r.get(3).availability(), "definitive=false must be UNKNOWN");
        assertFalse(r.get(3).definitive());

        assertEquals(Availability.UNKNOWN, r.get(4).availability());
        assertEquals("invalid domain name: bad_domain", r.get(4).error());
    }

    @Test
    void aDomainMissingFromTheResponseIsUnknown() {
        ProviderAvailability r = provider.checkAll(List.of("not-in-response.com")).get(0);
        assertEquals(Availability.UNKNOWN, r.availability());
        assertTrue(r.error() != null && !r.error().isBlank());
    }

    @Test
    void formatsMinorUnitsUsingTheCurrencyDecimals() {
        assertEquals("22.99 USD", DomainService.display(2299L, "USD"));
        assertEquals("2488.00 USD", DomainService.display(248800L, "USD"));
        assertEquals("1299 JPY", DomainService.display(1299L, "JPY"));
        assertNull(DomainService.display(null, "USD"));
    }
}
