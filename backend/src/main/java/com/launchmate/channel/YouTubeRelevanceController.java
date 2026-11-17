package com.launchmate.channel;

import com.launchmate.auth.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/youtube-relevance")
public class YouTubeRelevanceController {

    private final YouTubeRelevanceService relevance;

    public YouTubeRelevanceController(YouTubeRelevanceService relevance) {
        this.relevance = relevance;
    }

    /** Returns the latest assessment, or null (204-like empty body) if none exists. */
    @GetMapping
    public JsonNode latest(@PathVariable UUID profileId) {
        return relevance.latest(CurrentUser.id(), profileId);
    }

    @PostMapping
    public JsonNode assess(@PathVariable UUID profileId) {
        return relevance.assess(CurrentUser.id(), profileId);
    }
}
