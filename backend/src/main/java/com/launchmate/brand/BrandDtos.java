package com.launchmate.brand;

import com.launchmate.domain.DomainDtos.DomainResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class BrandDtos {

    private BrandDtos() {}

    public record BrandSuggestionRequest(
            @Size(max = 1000) String idea,
            @Size(max = 6) List<@Size(max = 40) String> tone,
            @Min(3) @Max(8) Integer count,
            Boolean checkAvailability) {}

    public record BrandNameView(String name, String tagline, String rationale, List<DomainResult> domains) {}

    /** {@code contentId} is the saved copy in generated_content. */
    public record BrandSuggestionResponse(UUID contentId, List<BrandNameView> names, List<String> positioning,
                                          List<String> limitations, String note) {}
}
