package com.launchmate.growth;

import com.launchmate.domain.DomainService;
import com.launchmate.growth.PlanInputs.Audit;
import com.launchmate.growth.PlanInputs.BrandName;
import com.launchmate.growth.PlanInputs.Channel;
import com.launchmate.growth.PlanInputs.DomainRow;
import com.launchmate.growth.PlanInputs.Finding;
import com.launchmate.growth.PlanInputs.Gap;
import com.launchmate.growth.PlanInputs.Progress;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileFacts;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads the owner's saved results out of the database and turns them into the plan's inputs. */
@Component
class PlanInputLoader {

    private static final List<String> ORIGIN_ORDER = List.of("CURRENT", "USER_CANDIDATE", "AI_SUGGESTED", "PROVIDER_SUGGESTED");
    private static final int MAX_FINDINGS = 12;
    private static final int MAX_FAILING_CHECKS = 6;
    private static final int MAX_DOMAINS = 8;
    private static final int MAX_BRAND_NAMES = 6;
    private static final int MAX_GAPS = 5;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    PlanInputLoader(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    PlanInputs load(ProfileResponse profile) {
        UUID id = profile.id();
        String type = profile.profileType();
        boolean creator = "YOUTUBE_CREATOR".equals(type);
        List<DomainRow> domains = creator ? List.of() : domains(id);
        List<BrandName> brand = creator ? List.of() : brandNames(id);
        return new PlanInputs(type, profile.displayName(), ProfileFacts.lines(profile),
                "NEW_BUSINESS".equals(type) ? launchFacts(profile, domains, brand) : List.of(),
                creator ? null : audit(id), channel(id), domains, brand, progress(id));
    }

    // ---- website audit ----------------------------------------------------------------------

    private Audit audit(UUID profileId) {
        record Row(UUID id, String url, Instant completed, String readiness, String speed, String geo, String domain) {}
        List<Row> rows = jdbc.query("""
                select id, website_url, completed_at, readiness_checks::text as readiness, pagespeed_data::text as speed,
                       geo_readiness::text as geo, domain_review::text as domain
                from website_audit where growth_profile_id = ? and status in ('COMPLETED', 'PARTIAL')
                order by created_at desc limit 1
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("website_url"),
                instant(rs.getTimestamp("completed_at")), rs.getString("readiness"), rs.getString("speed"),
                rs.getString("geo"), rs.getString("domain")), profileId);
        if (rows.isEmpty()) {
            return null;
        }
        Row r = rows.get(0);

        List<Finding> findings = jdbc.query("""
                select severity, category, title, evidence, recommendation from website_finding where audit_id = ?
                order by case severity when 'HIGH' then 0 when 'MEDIUM' then 1 else 2 end, title limit ?
                """, (rs, n) -> new Finding(rs.getString("severity"), rs.getString("category"), rs.getString("title"),
                rs.getString("evidence"), rs.getString("recommendation") == null ? "" : rs.getString("recommendation")),
                r.id(), MAX_FINDINGS);

        List<String> failing = new ArrayList<>();
        int assessed = 0;
        int passed = 0;
        if (r.readiness() != null) {
            for (JsonNode c : json.readTree(r.readiness())) {
                JsonNode p = c.get("passed");
                if (p == null || p.isNull()) {
                    continue;
                }
                assessed++;
                if (p.asBoolean()) {
                    passed++;
                } else if (failing.size() < MAX_FAILING_CHECKS) {
                    failing.add(c.path("key").asString() + ": FAIL - " + c.path("detail").asString());
                }
            }
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        if (r.speed() != null) {
            JsonNode speed = json.readTree(r.speed());
            if (speed.path("available").asBoolean(false)) {
                speed.path("scores").properties().forEach(e -> scores.put(e.getKey(), e.getValue().asInt()));
            }
        }
        List<String> geoLines = new ArrayList<>();
        if (r.geo() != null) {
            for (JsonNode sig : json.readTree(r.geo()).path("signals")) {
                geoLines.add(sig.path("status").asString() + ": " + sig.path("label").asString() + " - "
                        + sig.path("detail").asString());
            }
        }
        List<String> domainLines = new ArrayList<>();
        if (r.domain() != null) {
            JsonNode d = json.readTree(r.domain());
            for (JsonNode f : d.path("facts")) {
                if (!"UNAVAILABLE".equals(f.path("status").asString())) {
                    domainLines.add("measured " + f.path("status").asString() + ": " + f.path("label").asString()
                            + " - " + f.path("detail").asString());
                }
            }
            JsonNode ai = d.path("aiReview");
            if (ai.isObject()) {
                domainLines.add("AI-derived recommendation: " + ai.path("recommendation").asString() + " - "
                        + ai.path("reasoning").asString());
            }
        }
        return new Audit(r.id(), r.url(), r.completed(), passed + " of " + assessed + " checks passed",
                List.copyOf(failing), Map.copyOf(scores), List.copyOf(findings), List.copyOf(geoLines),
                List.copyOf(domainLines));
    }

    // ---- channel analysis -------------------------------------------------------------------

    private Channel channel(UUID profileId) {
        record Row(UUID id, Instant created, String stats, String analysis) {}
        List<Row> rows = jdbc.query("""
                select id, created_at, channel_stats::text as stats, analysis::text as analysis from channel_analysis
                where growth_profile_id = ? and status in ('COMPLETED', 'PARTIAL') and channel_stats is not null
                order by created_at desc limit 1
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), instant(rs.getTimestamp("created_at")),
                rs.getString("stats"), rs.getString("analysis")), profileId);
        if (rows.isEmpty()) {
            return null;
        }
        Row r = rows.get(0);
        JsonNode snapshot = json.readTree(r.stats());
        JsonNode stats = snapshot.path("stats");
        JsonNode channel = snapshot.path("channel");
        Instant measured = snapshot.hasNonNull("measuredAt") ? Instant.parse(snapshot.get("measuredAt").asString()) : r.created();

        List<Gap> gaps = new ArrayList<>();
        if (r.analysis() != null) {
            for (JsonNode g : json.readTree(r.analysis()).path("gaps")) {
                if (gaps.size() < MAX_GAPS) {
                    gaps.add(new Gap(g.path("title").asString(), g.path("evidence").asString(), g.path("suggestion").asString()));
                }
            }
        }
        String name = channel.path("title").asString() + (channel.hasNonNull("handle") ? " (" + channel.get("handle").asString() + ")" : "");
        return new Channel(r.id(), measured, name, statLines(stats), List.copyOf(gaps));
    }

    private static List<String> statLines(JsonNode s) {
        List<String> lines = new ArrayList<>();
        int videos = s.path("videosAnalysed").asInt();
        JsonNode c = s.path("cadence");
        if (c.hasNonNull("uploadsPerWeek")) {
            lines.add("uploads: about " + c.get("uploadsPerWeek").asString() + " per week (" + c.path("videos").asInt()
                    + " videos over " + c.path("spanDays").asString() + " days); median gap " + c.path("medianGapDays").asString()
                    + " days, longest gap " + c.path("longestGapDays").asString() + " days; days since the latest upload "
                    + c.path("daysSinceLatest").asString());
        } else {
            lines.add("uploads: not enough videos to measure the upload rate; days since the latest upload "
                    + c.path("daysSinceLatest").asString());
        }
        StringBuilder formats = new StringBuilder("format estimate from video length: ");
        for (JsonNode f : s.path("formats")) {
            String label = switch (f.path("format").asString()) {
                case "SHORT" -> "60 seconds or less";
                case "MID_LENGTH" -> "61 to 180 seconds";
                default -> "over 180 seconds";
            };
            formats.append(label).append(" ").append(f.path("videos").asInt()).append(" video(s); ");
        }
        lines.add(formats.toString().trim());
        JsonNode t = s.path("titles");
        lines.add("title style: average " + t.path("averageLength").asString() + " characters; numbers in "
                + t.path("withNumber").asInt() + " of " + videos + "; question marks in " + t.path("withQuestion").asInt()
                + "; emoji in " + t.path("withEmoji").asInt());
        JsonNode e = s.path("engagement");
        lines.add("engagement: median likes per 100 views " + (e.hasNonNull("likesPer100Views") ? e.get("likesPer100Views").asString() : "not available")
                + " (" + e.path("likesAvailable").asInt() + " of " + videos + " videos); comments per 100 views "
                + (e.hasNonNull("commentsPer100Views") ? e.get("commentsPer100Views").asString() : "not available"));
        lines.add("descriptions: " + s.path("descriptions").path("empty").asInt() + " of " + videos
                + " empty; videos with tags " + s.path("tags").path("videosWithTags").asInt() + " of " + videos);
        return lines;
    }

    // ---- domains and brand ------------------------------------------------------------------

    private List<DomainRow> domains(UUID profileId) {
        List<DomainRow> rows = jdbc.query("""
                select distinct on (domain_name) domain_name, origin, availability, price, currency, premium_fee,
                       checked_at, analysis::text as analysis
                from domain_suggestion where growth_profile_id = ? order by domain_name, created_at desc
                """, (rs, n) -> {
            Long price = (Long) rs.getObject("price");
            Long premium = (Long) rs.getObject("premium_fee");
            String currency = rs.getString("currency");
            String analysis = rs.getString("analysis");
            JsonNode a = analysis == null ? null : json.readTree(analysis);
            List<String> flags = new ArrayList<>();
            if (a != null) {
                a.path("flags").forEach(f -> flags.add(f.asString()));
            }
            return new DomainRow(rs.getString("domain_name"), rs.getString("origin"), rs.getString("availability"),
                    DomainService.display(price, currency), DomainService.display(premium, currency),
                    instant(rs.getTimestamp("checked_at")), a == null ? "NOT_PROVIDED" : a.path("brandMatch").asString("NOT_PROVIDED"),
                    List.copyOf(flags));
        }, profileId);
        List<DomainRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt((DomainRow d) -> ORIGIN_ORDER.indexOf(d.origin()))
                .thenComparing(DomainRow::domain));
        return List.copyOf(sorted.subList(0, Math.min(MAX_DOMAINS, sorted.size())));
    }

    private List<BrandName> brandNames(UUID profileId) {
        List<String> bodies = jdbc.query("""
                select body from generated_content
                where growth_profile_id = ? and metadata->>'kind' = 'BRAND_SUGGESTIONS'
                order by created_at desc limit 1
                """, (rs, n) -> rs.getString(1), profileId);
        List<BrandName> names = new ArrayList<>();
        if (!bodies.isEmpty()) {
            for (JsonNode n : json.readTree(bodies.get(0)).path("names")) {
                if (names.size() < MAX_BRAND_NAMES) {
                    names.add(new BrandName(n.path("name").asString(), n.path("tagline").asString()));
                }
            }
        }
        return List.copyOf(names);
    }

    // ---- progress and launch facts ----------------------------------------------------------

    private List<Progress> progress(UUID profileId) {
        return jdbc.query("""
                select r.status, r.module, r.title, r.note from recommendation r
                where r.plan_id = (select id from growth_plan where growth_profile_id = ? and status = 'COMPLETED'
                                   order by created_at desc limit 1)
                  and r.status <> 'OPEN'
                order by r.position
                """, (rs, n) -> new Progress(rs.getString("status"), rs.getString("module"), rs.getString("title"),
                rs.getString("note")), profileId);
    }

    private static List<String> launchFacts(ProfileResponse p, List<DomainRow> domains, List<BrandName> brand) {
        List<String> facts = new ArrayList<>();
        facts.add(p.websiteUrl() == null || p.websiteUrl().isBlank() ? "website address: none given"
                : "website address: " + p.websiteUrl());
        if (domains.isEmpty()) {
            facts.add("domain candidates checked: none yet");
        } else {
            List<String> available = domains.stream().filter(d -> "AVAILABLE".equals(d.availability()))
                    .map(DomainRow::domain).toList();
            facts.add("domain candidates checked: " + domains.size() + "; available when checked: "
                    + (available.isEmpty() ? "none" : String.join(", ", available)));
        }
        var b = p.businessDetails();
        facts.add(b == null || b.currentDomain() == null || b.currentDomain().isBlank()
                ? "current domain: none recorded" : "current domain: " + b.currentDomain());
        facts.add(b == null || b.draftCopy() == null || b.draftCopy().isBlank()
                ? "draft homepage copy: none provided" : "draft homepage copy: provided (" + b.draftCopy().length() + " characters)");
        int products = b == null || b.productsServices() == null ? 0 : b.productsServices().size();
        facts.add(products == 0 ? "products or services listed: none" : "products or services listed: " + products);
        facts.add(blank(p.targetAudience()) ? "target audience: not given" : "target audience: given");
        facts.add(blank(p.marketLocation()) ? "market: not given" : "market: given");
        facts.add(blank(p.primaryGoal()) ? "goal: not given" : "goal: given");
        facts.add(brand.isEmpty() ? "brand name suggestions: none generated yet"
                : "brand name suggestions: " + brand.size() + " generated; the owner has not recorded a chosen name");
        return List.copyOf(facts);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
