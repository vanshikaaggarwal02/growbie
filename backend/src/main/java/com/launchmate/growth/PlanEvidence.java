package com.launchmate.growth;

import com.launchmate.ai.Quotes;
import com.launchmate.growth.PlanInputs.Audit;
import com.launchmate.growth.PlanInputs.BrandName;
import com.launchmate.growth.PlanInputs.Channel;
import com.launchmate.growth.PlanInputs.DomainRow;
import com.launchmate.growth.PlanInputs.Finding;
import com.launchmate.growth.PlanInputs.Gap;
import com.launchmate.growth.PlanInputs.Progress;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the plain-text evidence the growth plan is written from. Every line the plan may rest on
 * carries an id in square brackets, such as [AUDIT-2] or [CHANNEL-G1]. A recommendation must cite
 * ids that exist here and quote a line of this text, so nothing in the plan can float free of data
 * that was actually measured or typed in.
 */
public final class PlanEvidence {

    public record Evidence(String text, Set<String> ids) {}

    private PlanEvidence() {}

    public static Evidence build(PlanInputs in) {
        StringBuilder sb = new StringBuilder();
        Set<String> ids = new LinkedHashSet<>();

        sb.append("PLAN SUBJECT: ").append(line(in.profileName())).append(" (").append(typeLabel(in.profileType()))
                .append(")\n");

        if (!in.ownerFacts().isEmpty()) {
            sb.append("\nOWNER-PROVIDED INFORMATION (typed in by the owner; not verified):\n");
            numbered(sb, ids, "OWNER", in.ownerFacts());
        }
        if (!in.launchFacts().isEmpty()) {
            sb.append("\nLAUNCH FACTS (read from the profile and saved results):\n");
            numbered(sb, ids, "LAUNCH", in.launchFacts());
        }

        Audit a = in.audit();
        if (a != null) {
            sb.append("\nWEBSITE AUDIT (completed ").append(a.completedAt().atZone(ZoneOffset.UTC).toLocalDate())
                    .append("; ").append(a.url()).append("):\n");
            sb.append("- ").append(tag(ids, "AUDIT-S")).append(" readiness: ").append(a.readinessSummary()).append("\n");
            int c = 0;
            for (String check : a.failingChecks()) {
                sb.append("- ").append(tag(ids, "AUDIT-C" + (++c))).append(" ").append(line(check)).append("\n");
            }
            if (a.pageSpeedScores() != null && !a.pageSpeedScores().isEmpty()) {
                sb.append("- ").append(tag(ids, "AUDIT-PSI")).append(" PageSpeed mobile scores: ")
                        .append(scores(a.pageSpeedScores())).append("\n");
            }
            int f = 0;
            for (Finding finding : a.findings()) {
                sb.append("- ").append(tag(ids, "AUDIT-" + (++f))).append(" ").append(finding.severity()).append(" ")
                        .append(finding.category()).append(" | ").append(line(finding.title()))
                        .append(" | evidence: ").append(line(finding.evidence()))
                        .append(" | fix: ").append(line(finding.recommendation())).append("\n");
            }
            if (a.geoLines() != null && !a.geoLines().isEmpty()) {
                sb.append("\nGEO READINESS (signals measured on the website; AI search tests have not been run):\n");
                int g = 0;
                for (String l : a.geoLines()) {
                    sb.append("- ").append(tag(ids, "GEO-" + (++g))).append(" ").append(line(l)).append("\n");
                }
            }
            if (a.domainLines() != null && !a.domainLines().isEmpty()) {
                sb.append("\nDOMAIN REVIEW OF THE BUSINESS'S OWN DOMAIN (facts are measured; the assessment is AI-derived):\n");
                int d = 0;
                for (String l : a.domainLines()) {
                    sb.append("- ").append(tag(ids, "DOMAINREVIEW-" + (++d))).append(" ").append(line(l)).append("\n");
                }
            }
        }

        Channel ch = in.channel();
        if (ch != null) {
            sb.append("\nCHANNEL ANALYSIS (measured ").append(ch.measuredAt().atZone(ZoneOffset.UTC).toLocalDate())
                    .append("): ").append(line(ch.channelName())).append("\n");
            int s = 0;
            for (String stat : ch.statLines()) {
                sb.append("- ").append(tag(ids, "CHANNEL-S" + (++s))).append(" ").append(line(stat)).append("\n");
            }
            int g = 0;
            for (Gap gap : ch.gaps()) {
                sb.append("- ").append(tag(ids, "CHANNEL-G" + (++g))).append(" ").append(line(gap.title()))
                        .append(" | evidence: ").append(line(gap.evidence()))
                        .append(" | suggestion: ").append(line(gap.suggestion())).append("\n");
            }
        }

        if (!in.domains().isEmpty()) {
            sb.append("\nDOMAINS CHECKED:\n");
            int d = 0;
            for (DomainRow row : in.domains()) {
                sb.append("- ").append(tag(ids, "DOMAIN-" + (++d))).append(" ").append(row.domain()).append(" (")
                        .append(row.origin()).append("): ").append(availability(row));
                if (row.priceDisplay() != null) {
                    sb.append(", indicative price ").append(row.priceDisplay()).append(" per year");
                }
                if (row.premiumFeeDisplay() != null) {
                    sb.append(", premium one-time fee ").append(row.premiumFeeDisplay());
                }
                sb.append("; brand match ").append(row.brandMatch()).append("; flags: ")
                        .append(row.flags().isEmpty() ? "none" : String.join(", ", row.flags())).append("\n");
            }
        }

        if (!in.brandNames().isEmpty()) {
            sb.append("\nBRAND NAME SUGGESTIONS (generated earlier; the owner has not chosen one):\n");
            int b = 0;
            for (BrandName name : in.brandNames()) {
                sb.append("- ").append(tag(ids, "BRAND-" + (++b))).append(" ").append(line(name.name())).append(" - ")
                        .append(line(name.tagline())).append("\n");
            }
        }

        if (!in.progress().isEmpty()) {
            sb.append("\nPROGRESS SO FAR (marked by the owner on the previous plan):\n");
            int p = 0;
            for (Progress prog : in.progress()) {
                sb.append("- ").append(tag(ids, "PROGRESS-" + (++p))).append(" ").append(prog.status()).append(" | ")
                        .append(prog.module()).append(" | ").append(line(prog.title()));
                if (prog.note() != null && !prog.note().isBlank()) {
                    sb.append(" | owner note: ").append(line(prog.note()));
                }
                sb.append("\n");
            }
        }

        sb.append("\nNOT AVAILABLE: sales, revenue, website traffic, social media analytics, competitor data and "
                + "private YouTube analytics (watch time, retention, impressions).\n");
        return new Evidence(sb.toString(), Set.copyOf(ids));
    }

    private static void numbered(StringBuilder sb, Set<String> ids, String prefix, List<String> lines) {
        int i = 0;
        for (String l : lines) {
            sb.append("- ").append(tag(ids, prefix + "-" + (++i))).append(" ").append(line(l)).append("\n");
        }
    }

    private static String tag(Set<String> ids, String id) {
        ids.add(id);
        return "[" + id + "]";
    }

    private static String availability(DomainRow r) {
        String when = r.checkedAt() == null ? "" : " when checked on " + r.checkedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return switch (r.availability()) {
            case "AVAILABLE" -> "available" + when;
            case "UNAVAILABLE" -> "not available" + when;
            case "UNKNOWN" -> "availability could not be confirmed";
            default -> "availability not checked";
        };
    }

    private static String scores(java.util.Map<String, Integer> scores) {
        StringBuilder sb = new StringBuilder();
        for (String key : List.of("performance", "accessibility", "bestPractices", "seo")) {
            if (scores.containsKey(key)) {
                sb.append(sb.length() == 0 ? "" : ", ")
                        .append(key.equals("bestPractices") ? "best practices" : key).append(" ").append(scores.get(key));
            }
        }
        return sb.toString();
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "NEW_BUSINESS" -> "new business, not yet launched";
            case "EXISTING_BUSINESS" -> "existing business with a website";
            default -> "YouTube creator";
        };
    }

    private static String line(String s) {
        return Quotes.collapse(s);
    }
}
