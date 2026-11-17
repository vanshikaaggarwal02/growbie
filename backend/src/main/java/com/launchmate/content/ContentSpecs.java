package com.launchmate.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What each kind of content looks like: its parts, their length limits, and how many variants to
 * give. The same specs drive the instructions sent to the model and the validation of its reply, so
 * the two can never drift apart.
 */
public final class ContentSpecs {

    public record PartSpec(String name, int min, int max, boolean required) {}

    public record Spec(String type, String label, int minVariants, int maxVariants, int defaultVariants,
                       List<PartSpec> parts, boolean creatorOnly, boolean needsTopic, boolean needsProduct) {

        public Optional<PartSpec> part(String name) {
            return parts.stream().filter(p -> p.name().equals(name)).findFirst();
        }
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        add(new Spec("HOMEPAGE_COPY", "Homepage copy", 1, 3, 2, parts(
                req("headline", 8, 90), req("subheadline", 20, 220), req("call_to_action", 2, 40),
                req("value_point_1", 20, 160), req("value_point_2", 20, 160), req("value_point_3", 20, 160),
                opt("about", 40, 600)), false, false, false));
        add(new Spec("META_TAGS", "Meta title and description", 1, 3, 2, parts(
                req("title", 20, 70), req("description", 60, 170)), false, false, false));
        add(new Spec("PRODUCT_DESCRIPTION", "Product description", 1, 2, 2, parts(
                req("headline", 5, 80), req("description", 40, 400), req("bullet_1", 10, 140),
                req("bullet_2", 10, 140), req("bullet_3", 10, 140), opt("bullet_4", 10, 140),
                opt("bullet_5", 10, 140)), false, false, true));
        add(new Spec("YT_TITLE", "YouTube titles", 5, 8, 6, parts(req("title", 10, 100)), true, true, false));
        add(new Spec("YT_DESCRIPTION", "YouTube description", 1, 2, 1, parts(
                req("opening", 20, 300), req("body", 30, 1500), req("call_to_action", 5, 200),
                opt("hashtags", 2, 60)), true, true, false));
        add(new Spec("YT_HOOK", "YouTube hooks", 5, 8, 5, parts(req("hook", 15, 220), opt("visual_note", 5, 200)),
                true, true, false));
        add(new Spec("YT_SCRIPT", "Short video script", 1, 2, 1, parts(
                req("title", 10, 100), req("hook", 15, 220), req("beat_1", 10, 260), req("beat_2", 10, 260),
                req("beat_3", 10, 260), opt("beat_4", 10, 260), opt("beat_5", 10, 260), opt("beat_6", 10, 260),
                opt("on_screen_text", 5, 200), req("call_to_action", 5, 160)), true, true, false));
    }

    private ContentSpecs() {}

    public static Optional<Spec> of(String type) {
        return Optional.ofNullable(SPECS.get(type));
    }

    /** The output-format instructions for one request, generated from the spec. */
    public static String instructions(Spec spec, int variants) {
        StringBuilder sb = new StringBuilder();
        sb.append("OUTPUT FORMAT: return exactly ").append(variants).append(" variant(s). Each variant has a label ")
                .append("(the angle it takes, under 60 characters), a reason, and these parts, using these exact names ")
                .append("and character limits:\n");
        for (PartSpec p : spec.parts()) {
            sb.append("- ").append(p.name()).append(": ").append(p.min()).append(" to ").append(p.max())
                    .append(" characters, ").append(p.required() ? "required" : "optional").append("\n");
        }
        return sb.toString();
    }

    private static void add(Spec spec) {
        SPECS.put(spec.type(), spec);
    }

    private static List<PartSpec> parts(PartSpec... parts) {
        return new ArrayList<>(List.of(parts));
    }

    private static PartSpec req(String name, int min, int max) {
        return new PartSpec(name, min, max, true);
    }

    private static PartSpec opt(String name, int min, int max) {
        return new PartSpec(name, min, max, false);
    }
}
