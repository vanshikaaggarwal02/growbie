package com.launchmate.website;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Request and response shapes for the website audit API (LLD section 6.3). */
public final class WebsiteAuditDtos {

    private WebsiteAuditDtos() {}

    /** The address defaults to the profile's website when omitted. */
    public record StartAuditRequest(@Size(max = 2048) String websiteUrl, @Min(1) @Max(10) Integer maxPages) {}

    public record StartAuditResponse(UUID auditId, String status) {}

    public record PageSummary(String url, int status, String title, String metaDescription, int wordCount,
                              int h1Count, long responseMs, boolean truncated) {}

    public record FindingView(UUID id, String category, String severity, String pageUrl, String title,
                              String evidence, String impact, String recommendation, String suggestedCopy,
                              String sourceLabel) {}

    /**
     * {@code pageSpeed} holds only values the API actually returned, or the reason it was unavailable.
     * {@code readinessChecks} entries with a null "passed" could not be assessed.
     */
    public record AuditView(UUID auditId, UUID profileId, String status, String websiteUrl, Instant createdAt,
                            Instant startedAt, Instant completedAt, List<PageSummary> pages, JsonNode pageSpeed,
                            JsonNode readinessChecks, String readinessSummary, String summary,
                            List<FindingView> findings, List<String> limitations, List<String> warnings,
                            JsonNode crawl, JsonNode domainReview, JsonNode geoReadiness) {}

    /** Findings per severity. There is no CRITICAL level: HIGH is the most serious. */
    public record SeverityCounts(int high, int medium, int low) {}

    public record AuditListItem(UUID auditId, String status, String websiteUrl, Instant createdAt,
                                Instant completedAt, String readinessSummary, String summary, int pagesRead,
                                SeverityCounts findings, Integer performanceScore, Integer seoScore) {}
}
