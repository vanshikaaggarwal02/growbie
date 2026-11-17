package com.launchmate.content;

import com.launchmate.ai.Quotes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the facts the model may write from. The same text is the guard text: a number or claim is
 * accepted in the generated copy only if it appears here. Titles and page text are included verbatim
 * and may contain instructions aimed at an automated reader; the system prompt says to ignore them.
 */
public final class ContentEvidence {

    public record Request(String type, String label, String topic, String tone, String productName, String pageUrl,
                          int variants) {}

    public record Site(String url, Instant auditedAt, String homepageTitle, String homepageDescription,
                       List<String> pageTitles, List<String> knownProblems) {}

    public record ChannelContext(String channelName, List<String> recentTitles, List<String> styleLines,
                                 List<String> themes, List<String> bestTitles) {}

    public record RecommendationContext(String title, String suggestedAction, String evidence) {}

    public record Context(Request request, List<String> ownerFacts, Site site, ChannelContext channel,
                          RecommendationContext recommendation, String original) {}

    /** {@code existingTitles} are the creator's current video titles, which new titles must not repeat. */
    public record Evidence(String text, Set<String> existingTitles) {}

    private ContentEvidence() {}

    public static Evidence build(Context c) {
        StringBuilder sb = new StringBuilder();
        Request r = c.request();
        sb.append("CONTENT REQUEST: ").append(r.label()).append(". Variants wanted: ").append(r.variants()).append(".\n");
        line(sb, "topic", r.topic());
        line(sb, "tone", r.tone());
        line(sb, "product", r.productName());
        line(sb, "page", r.pageUrl());

        if (c.original() != null && !c.original().isBlank()) {
            sb.append("\nCURRENT TEXT (the owner's existing wording; improve on it and do not copy it):\n");
            for (String l : c.original().split("\\R")) {
                if (!l.isBlank()) {
                    sb.append("  ").append(Quotes.collapse(l)).append("\n");
                }
            }
        }

        if (!c.ownerFacts().isEmpty()) {
            sb.append("\nOWNER-PROVIDED INFORMATION (typed in by the owner; not verified):\n");
            for (String f : c.ownerFacts()) {
                sb.append("- ").append(Quotes.collapse(f)).append("\n");
            }
        }

        Site s = c.site();
        if (s != null) {
            sb.append("\nCURRENT WEBSITE (audited ").append(s.auditedAt().atZone(ZoneOffset.UTC).toLocalDate())
                    .append("; ").append(s.url()).append("):\n");
            sb.append("- homepage title: ").append(orNone(s.homepageTitle())).append("\n");
            sb.append("- homepage meta description: ").append(orNone(s.homepageDescription())).append("\n");
            if (!s.pageTitles().isEmpty()) {
                sb.append("- page titles: ").append(String.join(" | ", s.pageTitles())).append("\n");
            }
            if (!s.knownProblems().isEmpty()) {
                sb.append("- problems the audit found: ").append(String.join("; ", s.knownProblems())).append("\n");
            }
        }

        Set<String> existing = new LinkedHashSet<>();
        ChannelContext ch = c.channel();
        if (ch != null) {
            sb.append("\nCHANNEL: ").append(Quotes.collapse(ch.channelName())).append("\n");
            if (!ch.recentTitles().isEmpty()) {
                sb.append("- recent video titles, newest first (do not repeat any of these):\n");
                for (String t : ch.recentTitles()) {
                    sb.append("  - ").append(Quotes.collapse(t)).append("\n");
                    existing.add(Quotes.collapse(t));
                }
            }
            for (String style : ch.styleLines()) {
                sb.append("- ").append(Quotes.collapse(style)).append("\n");
            }
            if (!ch.themes().isEmpty()) {
                sb.append("- recurring themes: ").append(String.join("; ", ch.themes())).append("\n");
            }
            if (!ch.bestTitles().isEmpty()) {
                sb.append("- videos with the most views per day: ").append(String.join(" | ", ch.bestTitles())).append("\n");
            }
        }

        RecommendationContext rec = c.recommendation();
        if (rec != null) {
            sb.append("\nLINKED RECOMMENDATION (the content should carry this out):\n");
            sb.append("- recommendation: ").append(Quotes.collapse(rec.title())).append("\n");
            sb.append("- suggested action: ").append(Quotes.collapse(rec.suggestedAction())).append("\n");
            sb.append("- evidence: ").append(Quotes.collapse(rec.evidence())).append("\n");
        }
        return new Evidence(sb.toString(), Set.copyOf(existing));
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(Quotes.collapse(value)).append("\n");
        }
    }

    private static String orNone(String s) {
        return s == null || s.isBlank() ? "(none)" : Quotes.collapse(s);
    }
}
