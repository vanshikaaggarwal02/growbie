package com.launchmate.domain;

import java.util.List;

/**
 * The domain lookup boundary. Only read operations exist: LaunchMate never registers a domain
 * or changes DNS. Users complete any purchase themselves on GoDaddy.
 */
public interface DomainProvider {

    /** Amounts are minor currency units exactly as the provider returned them (USD cents). */
    record ProviderPrice(long value, String currency, int period, Long renewal, Long premiumFee) {}

    /** {@code error} is set when the provider could not check this particular name. */
    record ProviderAvailability(String domain, Availability availability, boolean definitive,
                                String inventory, ProviderPrice price, String error) {}

    record ProviderSuggestion(String domain, String inventory, ProviderPrice price) {}

    String name();

    boolean enabled();

    /** Checks many domains in as few provider calls as possible. Results are keyed by domain name. */
    List<ProviderAvailability> checkAll(List<String> domains);

    default ProviderAvailability check(String domain) {
        return checkAll(List.of(domain)).get(0);
    }

    List<ProviderSuggestion> suggest(String keyword, List<String> tlds, int limit);
}
