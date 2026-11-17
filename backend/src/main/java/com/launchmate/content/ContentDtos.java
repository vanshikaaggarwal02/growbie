package com.launchmate.content;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Request and response shapes for the content studio (LLD section 6.6). */
public final class ContentDtos {

    private ContentDtos() {}

    /**
     * {@code topic} is required for the YouTube types and {@code productName} for product descriptions.
     * {@code originalText} is the existing wording to improve on; for meta tags it is filled in
     * from the latest website audit when left out.
     */
    public record GenerateRequest(
            @NotBlank @Pattern(regexp = "HOMEPAGE_COPY|META_TAGS|PRODUCT_DESCRIPTION|YT_TITLE|YT_DESCRIPTION|YT_HOOK|YT_SCRIPT")
            String contentType,
            @Size(max = 1000) String topic,
            @Size(max = 80) String tone,
            @Size(max = 120) String productName,
            @Size(max = 2048) @Pattern(regexp = "^$|https?://.+") String pageUrl,
            @Size(max = 2000) String originalText,
            UUID recommendationId,
            @Min(1) @Max(8) Integer count) {}

    /**
     * {@code variants} is a list of {label, parts:[{name,text}], reason}. {@code original} is the
     * wording the variants improve on, for a before-and-after view.
     */
    public record ContentView(UUID id, UUID profileId, UUID recommendationId, String contentType, String label,
                              String status, Integer selectedVariant, String original, JsonNode variants,
                              List<String> notes, JsonNode request, Instant createdAt, Instant updatedAt) {}

    public record UpdateContentRequest(
            @NotBlank @Pattern(regexp = "DRAFT|APPROVED") String status,
            @Min(1) @Max(8) Integer selectedVariant) {}
}
