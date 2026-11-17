package com.launchmate.website;

import com.launchmate.website.WebsiteAuditDtos.AuditView;
import com.launchmate.website.WebsiteAuditDtos.FindingView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** The difference between two audits of the same site. */
public record AuditComparison(Side from, Side to, List<CheckChange> checks, FindingChanges findings,
                              List<Delta> pageSpeedScores, List<MetricChange> pageSpeedMetrics, Totals totals,
                              String note, boolean evidenceChecked) {

    public record Side(UUID auditId, Instant createdAt, String websiteUrl, int pagesRead, boolean pageSpeedAvailable) {}

    /** change is FIXED, STILL_FAILING, NEWLY_FAILING, STILL_PASSING or NOT_ASSESSED. */
    public record CheckChange(String key, String label, Boolean before, Boolean after, String change) {}

    public record Persisting(FindingView before, FindingView after) {}

    public record FindingChanges(List<FindingView> resolved, List<Persisting> persisting, List<FindingView> added) {}

    public record Delta(String key, Integer before, Integer after, Integer change) {}

    public record MetricChange(String key, String before, String after) {}

    public record Totals(int fixedChecks, int stillFailingChecks, int newlyFailingChecks, int resolvedFindings,
                         int persistingFindings, int newFindings) {}

    static final String NOTE = "A finding is shown as resolved when the evidence it quoted no longer appears in what "
            + "the newer audit read. That means the problem is gone from the pages we read; it does not prove which "
            + "change fixed it, and pages that were not read in one of the audits cannot be compared.";

    static final String NOTE_BASIC = "One of these audits was saved before evidence was kept, so findings are "
            + "matched by their quoted text and wording only. Treat resolved and new findings as likely rather than "
            + "certain. The check results and PageSpeed numbers are exact.";

    static AuditComparison build(AuditView from, AuditView to) {
        return build(from, to, null, null);
    }

    /**
     * When both audits kept their evidence text, a finding is resolved only if its quoted evidence is absent
     * from the newer audit's evidence, and a finding is new only if its evidence was absent from the older
     * one. That stops the model's run-to-run wording differences from being reported as changes.
     */
    static AuditComparison build(AuditView from, AuditView to, String fromEvidence, String toEvidence) {
        boolean checked = fromEvidence != null && toEvidence != null;
        String fromHay = checked ? norm(fromEvidence) : "";
        String toHay = checked ? norm(toEvidence) : "";
        List<CheckChange> checks = new ArrayList<>();
        Map<String, JsonNode> before = byKey(from.readinessChecks());
        Map<String, JsonNode> after = byKey(to.readinessChecks());
        Set<String> keys = new LinkedHashSet<>(after.keySet());
        keys.addAll(before.keySet());
        int fixed = 0;
        int still = 0;
        int newly = 0;
        for (String k : keys) {
            JsonNode b = before.get(k);
            JsonNode a = after.get(k);
            Boolean pb = passed(b);
            Boolean pa = passed(a);
            String change;
            if (pb == null || pa == null) {
                change = "NOT_ASSESSED";
            } else if (!pb && pa) {
                change = "FIXED";
                fixed++;
            } else if (!pb) {
                change = "STILL_FAILING";
                still++;
            } else if (!pa) {
                change = "NEWLY_FAILING";
                newly++;
            } else {
                change = "STILL_PASSING";
            }
            String label = a != null ? a.path("label").asString() : b.path("label").asString();
            checks.add(new CheckChange(k, label, pb, pa, change));
        }

        List<FindingView> resolved = new ArrayList<>();
        List<Persisting> persisting = new ArrayList<>();
        List<FindingView> added = new ArrayList<>(to.findings());
        for (FindingView old : from.findings()) {
            FindingView match = null;
            for (FindingView candidate : added) {
                if (same(old, candidate)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null && checked && toHay.contains(norm(old.evidence()))) {
                persisting.add(new Persisting(old, old)); // still on the site; the model just did not raise it again
            } else if (match == null) {
                resolved.add(old);
            } else {
                persisting.add(new Persisting(old, match));
                added.remove(match);
            }
        }

        if (checked) {
            added.removeIf(f -> {
                boolean wasThere = fromHay.contains(norm(f.evidence()));
                if (wasThere) {
                    persisting.add(new Persisting(f, f)); // present before as well, only reported now
                }
                return wasThere;
            });
        }

        List<Delta> scores = new ArrayList<>();
        List<MetricChange> metrics = new ArrayList<>();
        JsonNode sb = from.pageSpeed() == null ? null : from.pageSpeed().path("scores");
        JsonNode sa = to.pageSpeed() == null ? null : to.pageSpeed().path("scores");
        Set<String> scoreKeys = new LinkedHashSet<>();
        addNames(scoreKeys, sa);
        addNames(scoreKeys, sb);
        for (String k : scoreKeys) {
            Integer b = number(sb, k);
            Integer a = number(sa, k);
            scores.add(new Delta(k, b, a, b == null || a == null ? null : a - b));
        }
        JsonNode mb = from.pageSpeed() == null ? null : from.pageSpeed().path("metrics");
        JsonNode ma = to.pageSpeed() == null ? null : to.pageSpeed().path("metrics");
        Set<String> metricKeys = new LinkedHashSet<>();
        addNames(metricKeys, ma);
        addNames(metricKeys, mb);
        for (String k : metricKeys) {
            metrics.add(new MetricChange(k, text(mb, k), text(ma, k)));
        }

        return new AuditComparison(side(from), side(to), checks,
                new FindingChanges(resolved, persisting, added), scores, metrics,
                new Totals(fixed, still, newly, resolved.size(), persisting.size(), added.size()), checked ? NOTE : NOTE_BASIC, checked);
    }

    private static Side side(AuditView a) {
        boolean ps = a.pageSpeed() != null && a.pageSpeed().path("available").asBoolean(false);
        return new Side(a.auditId(), a.createdAt(), a.websiteUrl(), a.pages().size(), ps);
    }

    private static Map<String, JsonNode> byKey(JsonNode checks) {
        Map<String, JsonNode> m = new LinkedHashMap<>();
        if (checks != null && checks.isArray()) {
            for (JsonNode c : checks) {
                m.put(c.path("key").asString(), c);
            }
        }
        return m;
    }

    private static Boolean passed(JsonNode c) {
        return c == null || c.get("passed") == null || c.get("passed").isNull() ? null : c.get("passed").asBoolean();
    }

    private static void addNames(Set<String> into, JsonNode obj) {
        if (obj != null && obj.isObject()) {
            obj.propertyNames().forEach(into::add);
        }
    }

    private static Integer number(JsonNode obj, String k) {
        return obj != null && obj.hasNonNull(k) && obj.get(k).isNumber() ? obj.get(k).asInt() : null;
    }

    private static String text(JsonNode obj, String k) {
        return obj != null && obj.hasNonNull(k) ? obj.get(k).asString() : null;
    }

    private static boolean same(FindingView a, FindingView b) {
        return a.category().equals(b.category())
                && (norm(a.evidence()).equals(norm(b.evidence())) || norm(a.title()).equals(norm(b.title())));
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
