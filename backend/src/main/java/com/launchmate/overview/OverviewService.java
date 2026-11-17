package com.launchmate.overview;

import com.launchmate.growth.GrowthPlanDtos.PlanListItem;
import com.launchmate.growth.GrowthPlanDtos.PlanView;
import com.launchmate.growth.GrowthPlanDtos.Progress;
import com.launchmate.growth.GrowthPlanDtos.RecommendationView;
import com.launchmate.growth.GrowthPlanService;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One view of the business for the dashboard. It only reads saved results, so it costs nothing to call and
 * never invents anything: each area is exactly one of ANALYSED, NOT_YET_ANALYSED, NOT_PROVIDED,
 * NOT_APPLICABLE or UNAVAILABLE.
 */
@Service
public class OverviewService {

    /** kind is ok (working), warn (needs attention) or gap (not known / not checked). */
    public record Point(String kind, String text) {}

    public record Area(String status, String note, List<Point> points, UUID latestId, Instant analysedAt) {}

    public record Domain(String host, String recommendation, String verdict, boolean reviewed) {}

    public record Overview(UUID profileId, String displayName, String path, String websiteUrl, Domain domain,
                           Area seo, Area geo, Area youtube, UUID planId, Progress progress,
                           RecommendationView nextStep, List<RecommendationView> roadmap, List<Report> reports) {}

    public record Report(String area, UUID id, Instant at, String result) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProfileService profiles;
    private final GrowthPlanService plans;

    public OverviewService(JdbcTemplate jdbc, ObjectMapper json, ProfileService profiles, GrowthPlanService plans) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.plans = plans;
    }

    private record AuditRow(UUID id, String status, Instant at, String url, String readiness, String speed,
                            String geo, String domain, String summary) {}

    public Overview get(UUID owner, UUID profileId) {
        ProfileResponse p = profiles.get(owner, profileId); // enforces ownership
        boolean hasWebsite = p.websiteUrl() != null && !p.websiteUrl().isBlank();
        boolean hasChannel = p.channels() != null && !p.channels().isEmpty();

        List<AuditRow> audits = jdbc.query("""
                select id, status, created_at, website_url, readiness_checks::text as readiness,
                       pagespeed_data::text as speed, geo_readiness::text as geo, domain_review::text as domain, summary
                from website_audit where growth_profile_id = ? order by created_at desc limit 5
                """, (rs, n) -> new AuditRow(rs.getObject("id", UUID.class), rs.getString("status"),
                at(rs.getTimestamp("created_at")), rs.getString("website_url"), rs.getString("readiness"),
                rs.getString("speed"), rs.getString("geo"), rs.getString("domain"), rs.getString("summary")), profileId);
        AuditRow done = audits.stream().filter(a -> a.status().equals("COMPLETED") || a.status().equals("PARTIAL"))
                .findFirst().orElse(null);
        AuditRow latest = audits.isEmpty() ? null : audits.get(0);

        Area seo = seoArea(hasWebsite, done, latest);
        Area geo = geoArea(hasWebsite, done, latest);
        Area youtube = youtubeArea(p, hasChannel);
        Domain domain = domain(done, p);

        UUID planId = null;
        Progress progress = null;
        RecommendationView next = null;
        List<RecommendationView> roadmap = List.of();
        PlanListItem item = plans.list(owner, profileId).stream()
                .filter(x -> x.status().equals("COMPLETED") || x.status().equals("PARTIAL")).findFirst().orElse(null);
        if (item != null) {
            PlanView plan = plans.get(owner, item.planId());
            planId = plan.planId();
            progress = plan.progress();
            roadmap = plan.recommendations().stream().filter(r -> !r.status().equals("DISMISSED")).limit(8).toList();
            next = plan.recommendations().stream()
                    .filter(r -> r.status().equals("OPEN") || r.status().equals("IN_PROGRESS")).findFirst().orElse(null);
        }

        List<Report> reports = new ArrayList<>();
        for (AuditRow a : audits) {
            if (a.status().equals("COMPLETED") || a.status().equals("PARTIAL")) {
                reports.add(new Report("SEO & Website", a.id(), a.at(), a.summary() == null ? "Audit saved" : first(a.summary())));
            }
        }
        jdbc.query("""
                select id, created_at from channel_analysis where growth_profile_id = ?
                and status in ('COMPLETED', 'PARTIAL') order by created_at desc limit 3
                """, (rs, n) -> reports.add(new Report("YouTube", rs.getObject("id", UUID.class),
                at(rs.getTimestamp("created_at")), "Channel analysed")), profileId);
        reports.sort((x, y) -> y.at().compareTo(x.at()));

        return new Overview(p.id(), p.displayName(), "NEW_BUSINESS".equals(p.profileType()) ? "LAUNCH" : "RUNNING",
                p.websiteUrl(), domain, seo, geo, youtube, planId, progress, next, roadmap,
                reports.size() > 6 ? reports.subList(0, 6) : reports);
    }

    private Area seoArea(boolean hasWebsite, AuditRow done, AuditRow latest) {
        if (!hasWebsite && done == null) {
            return new Area("NOT_PROVIDED", "No website address was given, so there is nothing to audit.", List.of(), null, null);
        }
        if (done == null) {
            if (latest != null && latest.status().equals("FAILED")) {
                return new Area("UNAVAILABLE", "The last audit could not finish.", List.of(), latest.id(), latest.at());
            }
            return new Area("NOT_YET_ANALYSED", "Run an audit to see how the website is doing.", List.of(), null, null);
        }
        List<Point> pts = new ArrayList<>();
        JsonNode checks = parse(done.readiness());
        int warns = 0;
        for (JsonNode c : checks) {
            if (c.has("passed") && c.get("passed").isBoolean() && !c.get("passed").asBoolean() && warns < 3) {
                pts.add(new Point("warn", c.path("label").asString() + ": " + c.path("detail").asString()));
                warns++;
            }
        }
        JsonNode scores = parse(done.speed()).path("scores");
        if (scores.hasNonNull("performance")) {
            int perf = scores.get("performance").asInt();
            pts.add(0, new Point(perf >= 90 ? "ok" : "warn", "Mobile performance score " + perf + " (Google PageSpeed)"));
        } else {
            pts.add(new Point("gap", "PageSpeed was unavailable for the latest audit"));
        }
        return new Area("ANALYSED", done.summary() == null ? null : first(done.summary()), pts, done.id(), done.at());
    }

    private Area geoArea(boolean hasWebsite, AuditRow done, AuditRow latest) {
        if (!hasWebsite && done == null) {
            return new Area("NOT_YET_ANALYSED", "GEO needs a website to read. It starts once you add one.", List.of(), null, null);
        }
        if (done == null || done.geo() == null) {
            return new Area("NOT_YET_ANALYSED", "Run an audit to check how ready the website is for AI search.", List.of(), null, null);
        }
        JsonNode geo = parse(done.geo());
        List<Point> pts = new ArrayList<>();
        int ok = 0;
        int warn = 0;
        int gap = 0;
        for (JsonNode s : geo.path("signals")) {
            String status = s.path("status").asString();
            if (status.equals("FAIL") && warn < 3) {
                pts.add(new Point("warn", s.path("label").asString() + ": " + s.path("detail").asString()));
                warn++;
            } else if (status.equals("PASS") && ok < 1) {
                pts.add(0, new Point("ok", s.path("label").asString()));
                ok++;
            } else if ((status.equals("NOT_ANALYSED") || status.equals("UNAVAILABLE")) && gap < 1) {
                pts.add(new Point("gap", s.path("label").asString() + ": not checked"));
                gap++;
            }
        }
        pts.add(new Point("gap", "AI search tests: not yet run"));
        return new Area("ANALYSED", geo.path("summary").asString(), pts, done.id(), done.at());
    }

    private Area youtubeArea(ProfileResponse p, boolean hasChannel) {
        if (!hasChannel) {
            return new Area("NOT_PROVIDED", "No YouTube channel was given. That is fine: YouTube is optional.", List.of(), null, null);
        }
        record Row(UUID id, String status, Instant at, String stats) {}
        List<Row> rows = jdbc.query("""
                select id, status, created_at, channel_stats::text as stats from channel_analysis
                where growth_profile_id = ? and status in ('COMPLETED', 'PARTIAL') order by created_at desc limit 1
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("status"),
                at(rs.getTimestamp("created_at")), rs.getString("stats")), p.id());
        if (rows.isEmpty()) {
            return new Area("NOT_YET_ANALYSED", "A channel was given. Run an analysis to see how it is doing.", List.of(), null, null);
        }
        Row r = rows.get(0);
        JsonNode st = parse(r.stats()).path("stats");
        List<Point> pts = new ArrayList<>();
        pts.add(new Point("ok", st.path("videosAnalysed").asInt() + " recent videos analysed"));
        for (JsonNode f : st.path("formats")) {
            if (f.path("format").asString().equals("SHORT") && f.path("videos").asInt() >= 0) {
                pts.add(new Point("ok", f.path("videos").asInt() + " Short(s) in the sample"));
            }
        }
        JsonNode cad = st.path("cadence");
        if (cad.hasNonNull("uploadsPerWeek")) {
            pts.add(new Point("ok", "About " + cad.get("uploadsPerWeek").asDouble() + " uploads per week"));
        }
        pts.add(new Point("gap", "Watch time and retention: unavailable without your authorisation"));
        return new Area("ANALYSED", null, pts, r.id(), r.at());
    }

    private Domain domain(AuditRow done, ProfileResponse p) {
        String host = p.websiteUrl() == null ? null : p.websiteUrl().replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        if (done == null || done.domain() == null) {
            return new Domain(host, null, null, false);
        }
        JsonNode d = parse(done.domain());
        JsonNode ai = d.path("aiReview");
        return new Domain(d.path("host").asString(host), ai.hasNonNull("recommendation") ? ai.get("recommendation").asString() : null,
                ai.hasNonNull("verdict") ? ai.get("verdict").asString() : null, ai.isObject());
    }

    private JsonNode parse(String text) {
        return text == null ? json.createObjectNode() : json.readTree(text);
    }

    private static String first(String summary) {
        int i = summary.indexOf(". ");
        return i > 0 && i < 220 ? summary.substring(0, i + 1) : summary.length() > 220 ? summary.substring(0, 217) + "..." : summary;
    }

    private static Instant at(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
