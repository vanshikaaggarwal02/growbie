package com.launchmate.growth;

import com.launchmate.auth.CurrentUser;
import com.launchmate.growth.GrowthPlanDtos.PlanListItem;
import com.launchmate.growth.GrowthPlanDtos.PlanView;
import com.launchmate.growth.GrowthPlanDtos.RecommendationView;
import com.launchmate.growth.GrowthPlanDtos.StartPlanResponse;
import com.launchmate.growth.GrowthPlanDtos.UpdateRecommendationRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GrowthPlanController {

    private final GrowthPlanService plans;

    public GrowthPlanController(GrowthPlanService plans) {
        this.plans = plans;
    }

    /** Returns at once; poll the plan until its status is COMPLETED or FAILED. */
    @PostMapping("/profiles/{profileId}/growth-plans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartPlanResponse start(@PathVariable UUID profileId) {
        return plans.start(CurrentUser.id(), profileId);
    }

    @GetMapping("/profiles/{profileId}/growth-plans")
    public List<PlanListItem> list(@PathVariable UUID profileId) {
        return plans.list(CurrentUser.id(), profileId);
    }

    @GetMapping("/growth-plans/{planId}")
    public PlanView get(@PathVariable UUID planId) {
        return plans.get(CurrentUser.id(), planId);
    }

    /** Marks a recommendation open, in progress, completed or dismissed, with an optional note. */
    @PatchMapping("/recommendations/{recommendationId}")
    public RecommendationView update(@PathVariable UUID recommendationId,
                                     @Valid @RequestBody UpdateRecommendationRequest request) {
        return plans.update(CurrentUser.id(), recommendationId, request);
    }
}
