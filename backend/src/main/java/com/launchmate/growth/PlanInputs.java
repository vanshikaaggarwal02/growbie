package com.launchmate.growth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the growth plan is built from, gathered from saved results, as plain values. */
public record PlanInputs(
        String profileType,
        String profileName,
        List<String> ownerFacts,
        List<String> launchFacts,
        Audit audit,
        Channel channel,
        List<DomainRow> domains,
        List<BrandName> brandNames,
        List<Progress> progress) {

    public record Audit(UUID id, String url, Instant completedAt, String readinessSummary,
                        List<String> failingChecks, Map<String, Integer> pageSpeedScores, List<Finding> findings,
                        List<String> geoLines, List<String> domainLines) {
        public Audit(UUID id, String url, Instant completedAt, String readinessSummary,
                     List<String> failingChecks, Map<String, Integer> pageSpeedScores, List<Finding> findings) {
            this(id, url, completedAt, readinessSummary, failingChecks, pageSpeedScores, findings, List.of(), List.of());
        }
    }

    public record Finding(String severity, String category, String title, String evidence, String recommendation) {}

    public record Channel(UUID id, Instant measuredAt, String channelName, List<String> statLines, List<Gap> gaps) {}

    public record Gap(String title, String evidence, String suggestion) {}

    public record DomainRow(String domain, String origin, String availability, String priceDisplay,
                            String premiumFeeDisplay, Instant checkedAt, String brandMatch, List<String> flags) {}

    public record BrandName(String name, String tagline) {}

    /** A recommendation from the previous plan that the owner has acted on. */
    public record Progress(String status, String module, String title, String note) {}

    public boolean hasAnalysis() {
        return audit != null || channel != null || !domains.isEmpty() || !brandNames.isEmpty();
    }
}
