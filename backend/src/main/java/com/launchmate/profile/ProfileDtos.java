package com.launchmate.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Request and response shapes for the profile API (LLD section 6.2). */
public final class ProfileDtos {

    private ProfileDtos() {}

    public record CreateProfileRequest(
            @NotBlank @Pattern(regexp = "NEW_BUSINESS|EXISTING_BUSINESS|YOUTUBE_CREATOR") String profileType,
            @NotBlank @Size(max = 160) String displayName,
            @Size(max = 2048) String websiteUrl,
            @Size(max = 2000) String targetAudience,
            @Size(max = 160) String marketLocation,
            @Size(max = 2000) String primaryGoal,
            @Size(max = 2048) String youtubeChannelUrl,
            @Valid BusinessDetailsDto businessDetails,
            @Valid CreatorDetailsDto creatorDetails) {}

    public record UpdateProfileRequest(
            @Size(max = 160) String displayName,
            @Size(max = 2048) String websiteUrl,
            @Size(max = 2000) String targetAudience,
            @Size(max = 160) String marketLocation,
            @Size(max = 2000) String primaryGoal,
            @Size(max = 2048) String youtubeChannelUrl,
            @Size(max = 253) String currentDomain,
            @Size(max = 2048) String googleBusinessUrl,
            @Size(max = 160) String launchTimeline,
            @Size(max = 160) String category,
            @Size(max = 4000) String description,
            List<@Size(max = 160) String> productsServices) {}

    public record BusinessDetailsDto(
            @Size(max = 160) String category,
            @Size(max = 4000) String description,
            @Size(max = 50) List<@Size(max = 160) String> productsServices,
            @Size(max = 8000) String draftCopy,
            @Size(max = 253) String currentDomain,
            @Size(max = 2048) String googleBusinessUrl,
            @Size(max = 160) String launchTimeline) {

        public BusinessDetailsDto(String category, String description, List<String> productsServices,
                                  String draftCopy, String currentDomain) {
            this(category, description, productsServices, draftCopy, currentDomain, null, null);
        }
    }

    public record CreatorDetailsDto(
            @Size(max = 160) String niche,
            @Size(max = 2000) String creatorBio,
            @Size(max = 80) String contentLanguage,
            @Size(max = 20) List<@Size(max = 200) String> contentGoals) {}

    public record BusinessDetailsView(String category, String description, JsonNode productsServices,
                                      String draftCopy, String currentDomain, String googleBusinessUrl,
                                      String launchTimeline) {}

    public record CreatorDetailsView(String niche, String creatorBio, String contentLanguage,
                                     JsonNode contentGoals) {}

    public record ChannelView(UUID id, String platform, String channelUrl, String connectionStatus,
                              Instant createdAt) {}

    public record ProfileResponse(UUID id, String profileType, String displayName, String websiteUrl,
                                  String targetAudience, String marketLocation, String primaryGoal,
                                  Instant createdAt, Instant updatedAt,
                                  BusinessDetailsView businessDetails, CreatorDetailsView creatorDetails,
                                  List<ChannelView> channels) {}
}
