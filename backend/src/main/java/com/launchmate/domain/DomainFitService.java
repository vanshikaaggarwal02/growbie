package com.launchmate.domain;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.common.ApiException;
import com.launchmate.domain.DomainDtos.AnalyseResponse;
import com.launchmate.domain.DomainDtos.DomainResult;
import com.launchmate.domain.DomainFitDtos.Assessment;
import com.launchmate.domain.DomainFitDtos.CurrentDomainReview;
import com.launchmate.domain.DomainFitDtos.FitItem;
import com.launchmate.domain.DomainFitDtos.FitRequest;
import com.launchmate.domain.DomainFitDtos.FitResponse;
import com.launchmate.domain.FitOutputParser.FitOutput;
import com.launchmate.profile.ProfileDtos.BusinessDetailsView;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Written domain assessment. The measured facts and the GoDaddy data are always returned; Claude's
 * assessment is added on top, and if that step fails the facts are still delivered with a warning.
 */
@Service
public class DomainFitService {

    private static final int MAX_DOMAINS = 10;

    private final ProfileService profiles;
    private final DomainService domains;
    private final ClaudeService claude;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DomainFitService(ProfileService profiles, DomainService domains, ClaudeService claude,
                            JdbcTemplate jdbc, ObjectMapper json) {
        this.profiles = profiles;
        this.domains = domains;
        this.claude = claude;
        this.jdbc = jdbc;
        this.json = json;
    }

    public FitResponse assess(UUID owner, UUID profileId, FitRequest request) {
        boolean check = request.checkAvailability() == null || request.checkAvailability();
        return assessWith(owner, profileId, request.domains(), check, null, null);
    }

    /**
     * Assesses the given domains. {@code currentOverride} marks one of them as the business's own domain
     * (the website audit passes the audited site); {@code siteFacts} are measured facts about that site,
     * given to the model as verified context.
     */
    public FitResponse assessWith(UUID owner, UUID profileId, List<String> requested, boolean check,
                                  String currentOverride, JsonNode siteFacts) {
        ProfileResponse profile = profiles.get(owner, profileId); // enforces ownership

        // An existing business's current domain is always included so its migration risks are covered.
        String current = null;
        BusinessDetailsView business = profile.businessDetails();
        if (currentOverride != null) {
            current = DomainNormaliser.normalise(currentOverride).orElse(null);
        } else if ("EXISTING_BUSINESS".equals(profile.profileType()) && business != null
                && business.currentDomain() != null) {
            current = DomainNormaliser.normalise(business.currentDomain()).orElse(null);
        }
        Set<String> wanted = new LinkedHashSet<>();
        if (requested != null) {
            for (String raw : requested) {
                Optional<String> d = DomainNormaliser.normalise(raw);
                d.ifPresent(wanted::add);
            }
        }
        if (current != null) {
            wanted.add(current);
        }
        if (wanted.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Provide at least one valid domain name to assess.");
        }
        if (wanted.size() > MAX_DOMAINS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "At most " + MAX_DOMAINS + " domains can be assessed at once.");
        }

        AnalyseResponse analysed = domains.analyseAs(owner, profileId, new ArrayList<>(wanted), check,
                "USER_CANDIDATE", current);
        List<DomainResult> results = analysed.results();
        Set<String> expected = results.stream().map(DomainResult::domain).collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> warnings = new ArrayList<>();
        FitOutput output = null;
        try {
            ClaudeRequest ai = new ClaudeRequest("domain_fit", profileId,
                    List.of("common_rules", "domain_fit"),
                    "Assess every domain in the EVIDENCE below. Give exactly one assessment per domain.\n\nEVIDENCE:\n"
                            + evidence(profile, results, current, siteFacts),
                    Model.CLAUDE_SONNET_5_5, 8000, OutputConfig.Effort.MEDIUM,
                    AiSchemas.format(AiSchemas.DOMAIN_FIT));
            final String currentDomain = current;
            output = claude.completeValidated(ai, text -> FitOutputParser.parse(json, text, expected, currentDomain));
        } catch (ApiException e) {
            warnings.add("The written assessment is unavailable right now (" + e.getMessage()
                    + ") The measured facts and domain data below are still accurate.");
        }

        List<FitItem> items = new ArrayList<>();
        for (DomainResult r : results) {
            Assessment assessment = output == null ? null : output.byDomain().get(r.domain());
            items.add(new FitItem(r, r.domain().equals(current), assessment));
        }
        CurrentDomainReview review = output == null ? null : output.currentReview();
        List<String> limitations = output == null ? List.of() : output.limitations();

        UUID contentId = output == null ? null : save(profileId, output);
        return new FitResponse(contentId, items, review, limitations, warnings, DomainService.NOTE);
    }

    private String evidence(ProfileResponse profile, List<DomainResult> results, String current,
                            JsonNode siteFacts) {
        Map<String, Object> business = new LinkedHashMap<>();
        put(business, "name", profile.displayName());
        put(business, "profileType", profile.profileType());
        put(business, "targetAudience", profile.targetAudience());
        put(business, "market", profile.marketLocation());
        BusinessDetailsView b = profile.businessDetails();
        if (b != null) {
            put(business, "category", b.category());
            put(business, "description", b.description());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (DomainResult r : results) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("domain", r.domain());
            d.put("isCurrentDomain", r.domain().equals(current));
            d.put("measuredFacts", r.properties());
            d.put("availability", r.availability());
            put(d, "inventory", r.inventory());
            put(d, "indicativeYearlyPrice", r.priceDisplay());
            put(d, "indicativeRenewalPrice", r.renewalPriceDisplay());
            put(d, "premiumOneTimeFee", r.premiumFeeDisplay());
            d.put("availabilitySource", r.checkedAt() == null ? "NOT_CHECKED" : "GODADDY_API");
            list.add(d);
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("business", business);
        evidence.put("domains", list);
        if (siteFacts != null) {
            evidence.put("siteFacts", siteFacts);
        }
        return json.writeValueAsString(evidence);
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private UUID save(UUID profileId, FitOutput output) {
        String body = json.writeValueAsString(Map.of(
                "assessments", output.byDomain(),
                "currentDomainReview", output.currentReview() == null ? Map.of() : output.currentReview(),
                "limitations", output.limitations()));
        return jdbc.queryForObject("""
                insert into generated_content (growth_profile_id, content_type, title, body, metadata)
                values (?, 'OTHER', 'Domain fit assessment', ?, '{"kind":"DOMAIN_FIT"}'::jsonb)
                returning id
                """, UUID.class, profileId, body);
    }
}
