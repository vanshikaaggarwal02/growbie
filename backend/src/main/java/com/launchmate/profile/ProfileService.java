package com.launchmate.profile;

import com.launchmate.common.ApiException;
import com.launchmate.profile.ProfileDtos.BusinessDetailsDto;
import com.launchmate.profile.ProfileDtos.BusinessDetailsView;
import com.launchmate.profile.ProfileDtos.ChannelView;
import com.launchmate.profile.ProfileDtos.CreateProfileRequest;
import com.launchmate.profile.ProfileDtos.CreatorDetailsDto;
import com.launchmate.profile.ProfileDtos.CreatorDetailsView;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileDtos.UpdateProfileRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Profile storage. Every read and write is scoped to the signed-in owner, and a profile that
 * belongs to someone else is reported as not found so its existence is not revealed.
 */
@Service
public class ProfileService {

    private static final Set<String> BUSINESS_TYPES = Set.of("NEW_BUSINESS", "EXISTING_BUSINESS");
    private static final String CORE = "id, profile_type, display_name, website_url, target_audience, "
            + "market_location, primary_goal, created_at, updated_at";
    private static final java.util.regex.Pattern YOUTUBE_URL = java.util.regex.Pattern.compile(
            "^https://(www\\.|m\\.)?(youtube\\.com|youtu\\.be)/.+", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProfileService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public ProfileResponse create(UUID owner, CreateProfileRequest r) {
        String type = r.profileType();
        boolean business = BUSINESS_TYPES.contains(type);
        if (business && r.creatorDetails() != null) {
            throw invalid("Creator details can only be used with a YouTube creator profile.");
        }
        if (!business && r.businessDetails() != null) {
            throw invalid("Business details can only be used with a business profile.");
        }
        String websiteUrl = optionalHttpUrl(r.websiteUrl());
        String channelUrl = optionalYoutubeUrl(r.youtubeChannelUrl());
        String googleUrl = business && r.businessDetails() != null
                ? optionalHttpUrl(r.businessDetails().googleBusinessUrl()) : null;
        if (!business && channelUrl == null) {
            throw invalid("A YouTube channel URL is required for a creator profile.");
        }

        UUID id = jdbc.queryForObject("""
                insert into growth_profile (owner_id, profile_type, display_name, website_url,
                    target_audience, market_location, primary_goal)
                values (?, ?, ?, ?, ?, ?, ?) returning id
                """, UUID.class, owner, type, r.displayName().trim(), websiteUrl,
                blankToNull(r.targetAudience()), blankToNull(r.marketLocation()), blankToNull(r.primaryGoal()));

        if (business) {
            BusinessDetailsDto b = r.businessDetails() != null
                    ? r.businessDetails() : new BusinessDetailsDto(null, null, null, null, null, null, null);
            jdbc.update("""
                    insert into business_details (growth_profile_id, category, description,
                        products_services, draft_copy, current_domain, google_business_url, launch_timeline)
                    values (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    """, id, blankToNull(b.category()), blankToNull(b.description()),
                    jsonOrNull(b.productsServices()), blankToNull(b.draftCopy()), blankToNull(b.currentDomain()),
                    googleUrl, blankToNull(b.launchTimeline()));
            if (channelUrl != null) {
                jdbc.update("insert into creator_channel (growth_profile_id, platform, channel_url) "
                        + "values (?, 'YOUTUBE', ?)", id, channelUrl);
            }
        } else {
            CreatorDetailsDto c = r.creatorDetails() != null
                    ? r.creatorDetails() : new CreatorDetailsDto(null, null, null, null);
            jdbc.update("""
                    insert into creator_details (growth_profile_id, niche, creator_bio,
                        content_language, content_goals)
                    values (?, ?, ?, ?, ?::jsonb)
                    """, id, blankToNull(c.niche()), blankToNull(c.creatorBio()),
                    blankToNull(c.contentLanguage()), jsonOrNull(c.contentGoals()));
            jdbc.update("insert into creator_channel (growth_profile_id, platform, channel_url) "
                    + "values (?, 'YOUTUBE', ?)", id, channelUrl);
        }
        return get(owner, id);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> list(UUID owner) {
        return jdbc.query("select " + CORE + " from growth_profile where owner_id = ? order by created_at desc",
                (rs, n) -> core(rs), owner);
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID owner, UUID id) {
        ProfileResponse c = findOwned(owner, id);
        BusinessDetailsView business = null;
        CreatorDetailsView creator = null;
        List<ChannelView> channels = List.of();
        if (BUSINESS_TYPES.contains(c.profileType())) {
            business = jdbc.query("""
                    select category, description, products_services::text as products, draft_copy, current_domain,
                           google_business_url, launch_timeline
                    from business_details where growth_profile_id = ?
                    """, (rs, n) -> new BusinessDetailsView(rs.getString("category"), rs.getString("description"),
                    readJson(rs.getString("products")), rs.getString("draft_copy"), rs.getString("current_domain"),
                    rs.getString("google_business_url"), rs.getString("launch_timeline")),
                    id).stream().findFirst().orElse(null);
        } else {
            creator = jdbc.query("""
                    select niche, creator_bio, content_language, content_goals::text as goals
                    from creator_details where growth_profile_id = ?
                    """, (rs, n) -> new CreatorDetailsView(rs.getString("niche"), rs.getString("creator_bio"),
                    rs.getString("content_language"), readJson(rs.getString("goals"))),
                    id).stream().findFirst().orElse(null);
        }
        channels = jdbc.query("""
                select id, platform, channel_url, connection_status, created_at
                from creator_channel where growth_profile_id = ? order by created_at
                """, (rs, n) -> new ChannelView(rs.getObject("id", UUID.class), rs.getString("platform"),
                rs.getString("channel_url"), rs.getString("connection_status"),
                rs.getTimestamp("created_at").toInstant()), id);
        return new ProfileResponse(c.id(), c.profileType(), c.displayName(), c.websiteUrl(), c.targetAudience(),
                c.marketLocation(), c.primaryGoal(), c.createdAt(), c.updatedAt(), business, creator, channels);
    }

    @Transactional
    public ProfileResponse update(UUID owner, UUID id, UpdateProfileRequest r) {
        findOwned(owner, id);
        jdbc.update("""
                update growth_profile set
                    display_name = coalesce(?, display_name),
                    website_url = coalesce(?, website_url),
                    target_audience = coalesce(?, target_audience),
                    market_location = coalesce(?, market_location),
                    primary_goal = coalesce(?, primary_goal),
                    updated_at = now()
                where id = ? and owner_id = ?
                """, blankToNull(r.displayName()), optionalHttpUrl(r.websiteUrl()),
                blankToNull(r.targetAudience()), blankToNull(r.marketLocation()), blankToNull(r.primaryGoal()),
                id, owner);

        // Business details that can be added or changed after onboarding. A blank value leaves the old one.
        if (BUSINESS_TYPES.contains(findOwned(owner, id).profileType())) {
            jdbc.update("""
                    update business_details set
                        category = coalesce(?, category),
                        description = coalesce(?, description),
                        products_services = coalesce(?::jsonb, products_services),
                        current_domain = coalesce(?, current_domain),
                        google_business_url = coalesce(?, google_business_url),
                        launch_timeline = coalesce(?, launch_timeline)
                    where growth_profile_id = ?
                    """, blankToNull(r.category()), blankToNull(r.description()),
                    r.productsServices() == null || r.productsServices().isEmpty() ? null : json.writeValueAsString(r.productsServices()),
                    blankToNull(r.currentDomain()), optionalHttpUrl(r.googleBusinessUrl()),
                    blankToNull(r.launchTimeline()), id);
        }
        String channelUrl = optionalYoutubeUrl(r.youtubeChannelUrl());
        if (channelUrl != null) {
            jdbc.update("delete from creator_channel where growth_profile_id = ? and platform = 'YOUTUBE'", id);
            jdbc.update("insert into creator_channel (growth_profile_id, platform, channel_url) values (?, 'YOUTUBE', ?)",
                    id, channelUrl);
        }
        return get(owner, id);
    }

    private ProfileResponse findOwned(UUID owner, UUID id) {
        return jdbc.query("select " + CORE + " from growth_profile where id = ? and owner_id = ?",
                        (rs, n) -> core(rs), id, owner)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found."));
    }

    private static ProfileResponse core(ResultSet rs) throws SQLException {
        return new ProfileResponse(rs.getObject("id", UUID.class), rs.getString("profile_type"),
                rs.getString("display_name"), rs.getString("website_url"), rs.getString("target_audience"),
                rs.getString("market_location"), rs.getString("primary_goal"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                null, null, List.of());
    }

    private String jsonOrNull(List<String> values) {
        return values == null ? null : json.writeValueAsString(values);
    }

    private JsonNode readJson(String text) {
        return text == null ? null : json.readTree(text);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Basic shape check only. The full SSRF guard runs before any server-side fetch (audit step). */
    private static String optionalHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String s = value.trim();
        try {
            URI uri = new URI(s);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null) {
                throw invalidUrl();
            }
            return s;
        } catch (URISyntaxException e) {
            throw invalidUrl();
        }
    }

    private static String optionalYoutubeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return com.launchmate.channel.ChannelRef.normalise(value);
        } catch (com.launchmate.channel.ChannelRef.Unsupported e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHANNEL_URL", e.getMessage());
        }
    }

    private static ApiException invalidUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBSITE_URL",
                "The website address must start with http:// or https:// and include a domain.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
