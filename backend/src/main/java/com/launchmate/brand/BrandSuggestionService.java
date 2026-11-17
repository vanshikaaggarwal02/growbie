package com.launchmate.brand;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.brand.BrandDtos.BrandNameView;
import com.launchmate.brand.BrandDtos.BrandSuggestionRequest;
import com.launchmate.brand.BrandDtos.BrandSuggestionResponse;
import com.launchmate.brand.BrandOutputParser.BrandName;
import com.launchmate.brand.BrandOutputParser.BrandOutput;
import com.launchmate.common.ApiException;
import com.launchmate.domain.DomainDtos.AnalyseResponse;
import com.launchmate.domain.DomainDtos.DomainResult;
import com.launchmate.domain.DomainService;
import com.launchmate.profile.ProfileDtos.BusinessDetailsView;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Brand names, taglines and positioning from Claude, then real domain availability from GoDaddy.
 * Claude proposes; Java validates the reply, normalises the domains and does all availability checks.
 */
@Service
public class BrandSuggestionService {

    private static final int DEFAULT_COUNT = 6;
    private static final int MAX_DOMAIN_CHECKS = 10;

    private final ProfileService profiles;
    private final ClaudeService claude;
    private final DomainService domains;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public BrandSuggestionService(ProfileService profiles, ClaudeService claude, DomainService domains,
                                  JdbcTemplate jdbc, ObjectMapper json) {
        this.profiles = profiles;
        this.claude = claude;
        this.domains = domains;
        this.jdbc = jdbc;
        this.json = json;
    }

    public BrandSuggestionResponse generate(UUID owner, UUID profileId, BrandSuggestionRequest request) {
        ProfileResponse profile = profiles.get(owner, profileId); // enforces ownership
        int count = request.count() == null ? DEFAULT_COUNT : request.count();
        boolean check = request.checkAvailability() == null || request.checkAvailability();

        ClaudeRequest ai = new ClaudeRequest("brand_suggestions", profileId,
                List.of("common_rules", "brand_suggestions"),
                "Generate brand suggestions for the business in the EVIDENCE below. Return "
                        + count + " names or fewer.\n\nEVIDENCE:\n" + evidence(profile, request, count),
                Model.CLAUDE_SONNET_5_5, 8000, OutputConfig.Effort.MEDIUM,
                AiSchemas.format(AiSchemas.BRAND_SUGGESTIONS));
        BrandOutput output = claude.completeValidated(ai, text -> BrandOutputParser.parse(json, text, count));

        // One batched, rate-limited availability check for every candidate (capped).
        Set<String> candidates = new LinkedHashSet<>();
        for (BrandName n : output.names()) {
            for (String d : n.domainCandidates()) {
                if (candidates.size() < MAX_DOMAIN_CHECKS) {
                    candidates.add(d);
                }
            }
        }
        Map<String, DomainResult> checked = new HashMap<>();
        if (!candidates.isEmpty()) {
            AnalyseResponse analysed = domains.analyseAs(owner, profileId, new ArrayList<>(candidates),
                    check, "AI_SUGGESTED", null);
            analysed.results().forEach(r -> checked.put(r.domain(), r));
        }

        List<BrandNameView> views = new ArrayList<>();
        for (BrandName n : output.names()) {
            List<DomainResult> forName = new ArrayList<>();
            for (String d : n.domainCandidates()) {
                if (checked.containsKey(d)) {
                    forName.add(checked.get(d));
                }
            }
            views.add(new BrandNameView(n.name(), n.tagline(), n.rationale(), forName));
        }

        UUID contentId = save(profileId, output);
        return new BrandSuggestionResponse(contentId, views, output.positioning(), output.limitations(),
                "Names are AI-generated suggestions. Trademark and existing-brand conflicts were not checked. "
                        + "Domain prices are indicative and availability can change; confirm both on GoDaddy.");
    }

    private String evidence(ProfileResponse profile, BrandSuggestionRequest request, int count) {
        Map<String, Object> p = new LinkedHashMap<>();
        put(p, "workingName", profile.displayName());
        put(p, "profileType", profile.profileType());
        put(p, "targetAudience", profile.targetAudience());
        put(p, "market", profile.marketLocation());
        put(p, "goal", profile.primaryGoal());
        BusinessDetailsView b = profile.businessDetails();
        if (b != null) {
            put(p, "category", b.category());
            put(p, "description", b.description());
            if (b.productsServices() != null) {
                p.put("productsServices", b.productsServices());
            }
            put(p, "draftCopy", b.draftCopy());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        put(r, "idea", request.idea());
        if (request.tone() != null && !request.tone().isEmpty()) {
            r.put("tone", request.tone());
        }
        r.put("namesWanted", count);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("business", p);
        evidence.put("request", r);
        return json.writeValueAsString(evidence);
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private UUID save(UUID profileId, BrandOutput output) {
        String body = json.writeValueAsString(output);
        return jdbc.queryForObject("""
                insert into generated_content (growth_profile_id, content_type, title, body, metadata)
                values (?, 'OTHER', 'Brand suggestions', ?, '{"kind":"BRAND_SUGGESTIONS"}'::jsonb)
                returning id
                """, UUID.class, profileId, body);
    }
}
