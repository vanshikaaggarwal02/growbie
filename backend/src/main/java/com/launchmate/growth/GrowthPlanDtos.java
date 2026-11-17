package com.launchmate.growth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Request and response shapes for the growth plan API (LLD section 6.6). */
public final class GrowthPlanDtos {

    private GrowthPlanDtos() {}

    public record StartPlanResponse(UUID planId, String status) {}

    /**
     * Impact and effort are qualitative ratings given with the evidence; priority is computed from
     * them. {@code sourceIds} point at the saved results the recommendation rests on.
     */
    public record RecommendationView(UUID id, int position, String module, String priority, String impact,
                                     String effort, String status, String title, String evidence, String rationale,
                                     String suggestedAction, String howToMeasure, List<String> sourceIds,
                                     String sourceLabel, String note, Instant completedAt, Instant updatedAt,
                                     String confidence) {}

    public record Progress(int total, int open, int inProgress, int completed, int dismissed) {}

    public record PlanView(UUID planId, UUID profileId, String status, Instant createdAt, Instant startedAt,
                           Instant completedAt, String summary, List<RecommendationView> recommendations,
                           Progress progress, List<String> limitations, List<String> warnings, JsonNode inputs) {}

    public record PlanListItem(UUID planId, String status, Instant createdAt, String summary, Progress progress) {}

    public record UpdateRecommendationRequest(
            @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|COMPLETED|DISMISSED") String status,
            @Size(max = 500) String note) {}
}
