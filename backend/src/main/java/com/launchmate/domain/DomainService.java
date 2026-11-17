package com.launchmate.domain;

import com.launchmate.common.ApiException;
import com.launchmate.domain.DomainAnalyzer.DomainProperties;
import com.launchmate.domain.DomainDtos.AnalyseRequest;
import com.launchmate.domain.DomainDtos.AnalyseResponse;
import com.launchmate.domain.DomainDtos.DomainResult;
import com.launchmate.domain.DomainDtos.Rejected;
import com.launchmate.domain.DomainDtos.SuggestRequest;
import com.launchmate.domain.DomainDtos.SuggestResponse;
import com.launchmate.domain.DomainProvider.ProviderAvailability;
import com.launchmate.domain.DomainProvider.ProviderPrice;
import com.launchmate.domain.DomainProvider.ProviderSuggestion;
import com.launchmate.profile.ProfileService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Domain analysis and lookup. Network calls are deliberately not wrapped in a database transaction;
 * each result is saved on its own once it is known. A whole request costs one batched provider call.
 */
@Service
public class DomainService {

    static final String NOTE =
            "Prices are indicative and availability can change. Confirm both on GoDaddy before buying.";

    /** Outcome of one batched availability check. {@code failure} is set when the whole call failed. */
    private record Batch(Map<String, ProviderAvailability> byDomain, String failure) {
        static final Batch NONE = new Batch(Map.of(), null);
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DomainProvider provider;
    private final ProfileService profiles;

    public DomainService(JdbcTemplate jdbc, ObjectMapper json, DomainProvider provider, ProfileService profiles) {
        this.jdbc = jdbc;
        this.json = json;
        this.provider = provider;
        this.profiles = profiles;
    }

    public AnalyseResponse analyse(UUID owner, UUID profileId, AnalyseRequest request) {
        boolean check = request.checkAvailability() == null || request.checkAvailability();
        return analyseAs(owner, profileId, request.domains(), check, "USER_CANDIDATE", null);
    }

    /**
     * Normalises, checks (one batched provider call) and saves the given domains.
     * {@code defaultOrigin} labels where the names came from; {@code currentDomain}, when given and
     * already normalised, marks the business's existing domain with origin CURRENT.
     */
    public AnalyseResponse analyseAs(UUID owner, UUID profileId, List<String> domains, boolean check,
                                     String defaultOrigin, String currentDomain) {
        String brand = profiles.get(owner, profileId).displayName(); // also enforces ownership

        Set<String> accepted = new LinkedHashSet<>();
        List<Rejected> rejected = new ArrayList<>();
        for (String raw : domains) {
            Optional<String> normalised = DomainNormaliser.normalise(raw);
            if (normalised.isPresent()) {
                accepted.add(normalised.get());
            } else {
                rejected.add(new Rejected(raw, "This is not a valid domain name."));
            }
        }

        Batch batch = check && provider.enabled() ? checkBatch(new ArrayList<>(accepted)) : Batch.NONE;
        List<DomainResult> results = new ArrayList<>();
        for (String domain : accepted) {
            String origin = domain.equals(currentDomain) ? "CURRENT" : defaultOrigin;
            results.add(evaluate(profileId, brand, domain, origin, null, check, batch));
        }
        return new AnalyseResponse(results, rejected, NOTE);
    }

    public SuggestResponse suggest(UUID owner, UUID profileId, SuggestRequest request) {
        String brand = profiles.get(owner, profileId).displayName();
        if (!provider.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DOMAIN_CHECK_UNAVAILABLE",
                    "Domain lookup is not configured.");
        }
        int limit = request.limit() == null ? 5 : request.limit();
        boolean check = Boolean.TRUE.equals(request.checkAvailability());

        Map<String, ProviderSuggestion> suggestions = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (ProviderSuggestion s : provider.suggest(request.keyword().trim(), request.tlds(), limit)) {
            Optional<String> domain = DomainNormaliser.normalise(s.domain());
            if (domain.isPresent() && suggestions.putIfAbsent(domain.get(), s) == null) {
                order.add(domain.get());
            }
        }

        Batch batch = check ? checkBatch(order) : Batch.NONE;
        List<DomainResult> results = new ArrayList<>();
        for (String domain : order) {
            results.add(evaluate(profileId, brand, domain, "PROVIDER_SUGGESTED", suggestions.get(domain),
                    check, batch));
        }
        return new SuggestResponse(results, NOTE);
    }

    public List<DomainResult> list(UUID owner, UUID profileId) {
        profiles.get(owner, profileId);
        return jdbc.query("""
                select domain_name, origin, availability, inventory, price, currency, period,
                       renewal_price, premium_fee, checked_at, analysis::text as analysis, provider
                from domain_suggestion where growth_profile_id = ?
                order by created_at desc limit 50
                """, (rs, n) -> {
            Long price = (Long) rs.getObject("price");
            ProviderPrice pp = price == null ? null : new ProviderPrice(price, rs.getString("currency"),
                    rs.getInt("period"), (Long) rs.getObject("renewal_price"), (Long) rs.getObject("premium_fee"));
            Timestamp checked = rs.getTimestamp("checked_at");
            String analysis = rs.getString("analysis");
            return build(rs.getString("domain_name"), rs.getString("origin"),
                    Availability.valueOf(rs.getString("availability")), rs.getString("inventory"), pp,
                    checked == null ? null : checked.toInstant(),
                    analysis == null ? null : json.readTree(analysis), List.of(), rs.getString("provider"));
        }, profileId);
    }

    private Batch checkBatch(List<String> domains) {
        if (domains.isEmpty()) {
            return Batch.NONE;
        }
        try {
            Map<String, ProviderAvailability> byDomain = new HashMap<>();
            for (ProviderAvailability pa : provider.checkAll(domains)) {
                byDomain.put(pa.domain(), pa);
            }
            return new Batch(byDomain, null);
        } catch (ApiException e) {
            return new Batch(Map.of(), e.getMessage());
        }
    }

    private DomainResult evaluate(UUID profileId, String brand, String domain, String origin,
                                  ProviderSuggestion suggestion, boolean check, Batch batch) {
        DomainProperties props = DomainAnalyzer.analyse(domain, brand);
        List<String> extra = new ArrayList<>();
        Availability availability = Availability.NOT_CHECKED;
        String inventory = suggestion == null ? null : suggestion.inventory();
        ProviderPrice price = suggestion == null ? null : suggestion.price();
        Instant checkedAt = null;

        if (check && !provider.enabled()) {
            extra.add("Domain lookup is not configured, so availability was not checked.");
        } else if (check) {
            ProviderAvailability pa = batch.byDomain().get(domain);
            if (pa == null) {
                availability = Availability.UNKNOWN;
                extra.add(batch.failure() != null ? batch.failure()
                        : "The lookup did not return a result for this name.");
            } else {
                availability = pa.availability();
                inventory = pa.inventory() != null ? pa.inventory() : inventory;
                price = pa.price() != null ? pa.price() : price;
                if (pa.error() == null) {
                    checkedAt = Instant.now();
                } else {
                    extra.add("The provider could not check this name: " + pa.error());
                }
            }
        } else if (suggestion != null) {
            // GoDaddy's suggestions endpoint returns available names only (confirmed with real checks).
            availability = Availability.AVAILABLE;
            checkedAt = Instant.now();
            extra.add("Suggested by GoDaddy as available. Availability can change, so confirm before buying.");
        }

        JsonNode propertiesJson = json.valueToTree(props);
        jdbc.update("""
                insert into domain_suggestion (growth_profile_id, domain_name, origin, availability,
                    price, currency, period, renewal_price, premium_fee, inventory, analysis, provider, checked_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, profileId, domain, origin, availability.name(),
                price == null ? null : price.value(), price == null ? null : price.currency(),
                price == null ? null : price.period(), price == null ? null : price.renewal(),
                price == null ? null : price.premiumFee(), inventory,
                json.writeValueAsString(propertiesJson), provider.name(),
                checkedAt == null ? null : Timestamp.from(checkedAt));

        return build(domain, origin, availability, inventory, price, checkedAt, propertiesJson, extra,
                provider.name());
    }

    private static DomainResult build(String domain, String origin, Availability availability, String inventory,
                                      ProviderPrice price, Instant checkedAt, JsonNode properties,
                                      List<String> extraNotes, String source) {
        List<String> notes = new ArrayList<>(extraNotes);
        String currency = price == null ? null : price.currency();
        if (price != null) {
            notes.add("Indicative price from GoDaddy. Confirm the final price at checkout.");
        }
        boolean premium = "PREMIUM".equalsIgnoreCase(inventory)
                || (price != null && price.premiumFee() != null && price.premiumFee() > 0);
        if (premium) {
            String fee = price == null ? null : display(price.premiumFee(), currency);
            notes.add(fee == null
                    ? "Premium domain: an extra one-time fee applies on top of the yearly price."
                    : "Premium domain: an extra one-time fee of " + fee + " applies on top of the yearly price.");
        }
        switch (availability) {
            case UNAVAILABLE -> notes.add("This domain is not available for registration.");
            case UNKNOWN -> notes.add("Availability could not be confirmed. Check again before deciding.");
            case NOT_CHECKED -> notes.add("Availability was not checked.");
            default -> { }
        }
        return new DomainResult(domain, origin, availability.name(), inventory,
                price == null ? null : price.value(), currency, price == null ? null : price.period(),
                price == null ? null : price.renewal(), price == null ? null : price.premiumFee(),
                price == null ? null : display(price.value(), currency),
                price == null ? null : display(price.renewal(), currency),
                price == null ? null : display(price.premiumFee(), currency),
                checkedAt, properties, List.copyOf(notes), source);
    }

    /** Formats a minor-unit amount using the currency's own number of decimal places. */
    public static String display(Long minor, String currencyCode) {
        if (minor == null || currencyCode == null) {
            return null;
        }
        try {
            int digits = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
            return BigDecimal.valueOf(minor, digits).toPlainString() + " " + currencyCode;
        } catch (IllegalArgumentException e) {
            return minor + " " + currencyCode + " (minor units)";
        }
    }
}
