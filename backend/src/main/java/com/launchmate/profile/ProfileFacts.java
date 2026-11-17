package com.launchmate.profile;

import com.launchmate.profile.ProfileDtos.BusinessDetailsView;
import com.launchmate.profile.ProfileDtos.CreatorDetailsView;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** What the owner typed about themselves, as plain "label: value" lines for the model's evidence. */
public final class ProfileFacts {

    private ProfileFacts() {}

    public static List<String> lines(ProfileResponse p) {
        List<String> facts = new ArrayList<>();
        add(facts, "name", p.displayName());
        add(facts, "audience", p.targetAudience());
        add(facts, "market", p.marketLocation());
        add(facts, "goal", p.primaryGoal());
        BusinessDetailsView b = p.businessDetails();
        if (b != null) {
            add(facts, "category", b.category());
            add(facts, "description", b.description());
            add(facts, "products or services", join(b.productsServices(), ", "));
            add(facts, "draft homepage copy", b.draftCopy());
            add(facts, "current domain", b.currentDomain());
            add(facts, "Google Business Profile link", b.googleBusinessUrl());
            add(facts, "planned opening", b.launchTimeline());
        }
        CreatorDetailsView c = p.creatorDetails();
        if (c != null) {
            add(facts, "niche", c.niche());
            add(facts, "about the creator", c.creatorBio());
            add(facts, "content language", c.contentLanguage());
            add(facts, "content goals", join(c.contentGoals(), "; "));
        }
        return List.copyOf(facts);
    }

    private static String join(JsonNode array, String separator) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        array.forEach(n -> items.add(n.asString()));
        return String.join(separator, items);
    }

    private static void add(List<String> facts, String label, String value) {
        if (value != null && !value.isBlank()) {
            String v = value.trim();
            facts.add(label + ": " + (v.length() > 600 ? v.substring(0, 600) : v));
        }
    }
}
