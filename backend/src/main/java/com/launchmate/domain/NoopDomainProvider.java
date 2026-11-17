package com.launchmate.domain;

import com.launchmate.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Used when no GoDaddy token is configured. Domains are analysed but never checked. */
public class NoopDomainProvider implements DomainProvider {

    @Override
    public String name() {
        return "NONE";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public List<ProviderAvailability> checkAll(List<String> domains) {
        return domains.stream()
                .map(d -> new ProviderAvailability(d, Availability.NOT_CHECKED, false, null, null, null))
                .toList();
    }

    @Override
    public List<ProviderSuggestion> suggest(String keyword, List<String> tlds, int limit) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DOMAIN_CHECK_UNAVAILABLE",
                "Domain lookup is not configured.");
    }
}
