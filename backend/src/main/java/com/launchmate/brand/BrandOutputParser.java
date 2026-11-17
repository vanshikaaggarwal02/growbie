package com.launchmate.brand;

import com.launchmate.ai.AiJson;
import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.domain.DomainNormaliser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates the model's brand suggestions. Nothing that fails these checks is shown or saved. */
final class BrandOutputParser {

    record BrandName(String name, String tagline, String rationale, List<String> domainCandidates) {}

    record BrandOutput(List<BrandName> names, List<String> positioning, List<String> limitations) {}

    private BrandOutputParser() {}

    static BrandOutput parse(ObjectMapper json, String text, int maxNames) {
        JsonNode root = AiJson.parseObject(json, text);

        List<BrandName> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode n : AiJson.array(root, "names", 1, maxNames)) {
            if (!n.isObject()) {
                throw new InvalidAiOutputException("Every item in 'names' must be an object.");
            }
            String name = AiJson.str(n, "name", 1, 60);
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                continue; // a repeated name adds nothing
            }
            String tagline = AiJson.str(n, "tagline", 1, 120);
            String rationale = AiJson.str(n, "rationale", 1, 400);
            List<String> raw = AiJson.strings(n, "domainCandidates", 0, 2, 253);

            // Keep only candidates that are valid domain names; the rest are dropped, not fatal.
            List<String> domains = new ArrayList<>();
            for (String candidate : raw) {
                Optional<String> normalised = DomainNormaliser.normalise(candidate);
                if (normalised.isPresent() && !domains.contains(normalised.get())) {
                    domains.add(normalised.get());
                }
            }
            names.add(new BrandName(name, tagline, rationale, List.copyOf(domains)));
        }

        return new BrandOutput(List.copyOf(names),
                AiJson.strings(root, "positioning", 0, 4, 300),
                AiJson.strings(root, "limitations", 0, 5, 300));
    }
}
