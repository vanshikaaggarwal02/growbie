package com.launchmate.domain;

import com.launchmate.domain.DomainDtos.DomainResult;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class DomainFitDtos {

    private DomainFitDtos() {}

    /** {@code domains} may be empty for an existing business that already has a current domain. */
    public record FitRequest(
            @Size(max = 10) List<@Size(max = 253) String> domains,
            Boolean checkAvailability) {}

    public record Assessment(String verdict, List<String> strengths, List<String> concerns, String suggestion) {}

    public record CurrentDomainReview(String domain, String recommendation, String reasoning,
                                      List<String> risks, List<String> checklist) {}

    /** {@code assessment} is null when the written assessment was unavailable; the facts are still real. */
    public record FitItem(DomainResult domain, boolean currentDomain, Assessment assessment) {}

    public record FitResponse(UUID contentId, List<FitItem> items, CurrentDomainReview currentDomainReview,
                              List<String> limitations, List<String> warnings, String note) {}
}
