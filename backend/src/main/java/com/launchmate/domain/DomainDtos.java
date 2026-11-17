package com.launchmate.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Request and response shapes for the domain API (LLD section 6.5). */
public final class DomainDtos {

    private DomainDtos() {}

    public record AnalyseRequest(
            @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 253) String> domains,
            Boolean checkAvailability) {}

    public record SuggestRequest(
            @NotBlank @Size(max = 80) String keyword,
            @Size(max = 5) List<@Pattern(regexp = "[a-z]{2,10}") String> tlds,
            @Min(1) @Max(10) Integer limit,
            Boolean checkAvailability) {}

    /**
     * Amounts ending in "Minor" are the provider's raw values (USD cents); the "Display" fields are
     * formatted from the currency's own decimal places. Prices are indicative, not a checkout quote.
     */
    public record DomainResult(
            String domain,
            String origin,
            String availability,
            String inventory,
            Long priceMinor,
            String currency,
            Integer periodYears,
            Long renewalPriceMinor,
            Long premiumFeeMinor,
            String priceDisplay,
            String renewalPriceDisplay,
            String premiumFeeDisplay,
            Instant checkedAt,
            JsonNode properties,
            List<String> notes,
            String source) {}

    public record Rejected(String input, String reason) {}

    public record AnalyseResponse(List<DomainResult> results, List<Rejected> rejected, String note) {}

    public record SuggestResponse(List<DomainResult> results, String note) {}
}
