package com.launchmate.website;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.common.ApiException;
import com.launchmate.domain.DomainFitDtos.FitResponse;
import com.launchmate.domain.DomainFitService;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileService;
import com.launchmate.website.AuditEvidence.Evidence;
import com.launchmate.website.AuditOutputParser.Finding;
import com.launchmate.website.AuditOutputParser.Output;
import com.launchmate.website.ReadinessChecks.Check;
import com.launchmate.website.WebsiteAuditDtos.AuditListItem;
import com.launchmate.website.WebsiteAuditDtos.AuditView;
import com.launchmate.website.WebsiteAuditDtos.FindingView;
import com.launchmate.website.WebsiteAuditDtos.PageSummary;
import com.launchmate.website.WebsiteAuditDtos.SeverityCounts;
import com.launchmate.website.WebsiteAuditDtos.StartAuditRequest;
import com.launchmate.website.WebsiteAuditDtos.StartAuditResponse;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs website audits in the background. A request returns at once with an id; the work (crawl,
 * PageSpeed, the model's findings) runs on a small bounded pool and the state moves through
 * QUEUED, RUNNING and then COMPLETED, PARTIAL or FAILED. Whatever succeeded is always saved.
 */
@Service
public class WebsiteAuditService {

    private static final Logger log = LoggerFactory.getLogger(WebsiteAuditService.class);
    private static final int DEFAULT_PAGES = 5;
    private static final int AUDITS_PER_HOUR = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProfileService profiles;
    private final UrlGuard guard;
    private final WebsiteCrawler crawler;
    private final PageSpeedClient pageSpeed;
    private final ClaudeService claude;
    private final SafeFetcher fetcher;
    private final DomainFitService domainFit;
    private final ThreadPoolExecutor executor;

    public WebsiteAuditService(JdbcTemplate jdbc, ObjectMapper json, ProfileService profiles, UrlGuard guard,
                               WebsiteCrawler crawler, PageSpeedClient pageSpeed, ClaudeService claude,
                               SafeFetcher fetcher, DomainFitService domainFit,
                               @Qualifier("analysisExecutor") ThreadPoolExecutor executor) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.guard = guard;
        this.crawler = crawler;
        this.pageSpeed = pageSpeed;
        this.claude = claude;
        this.fetcher = fetcher;
        this.domainFit = domainFit;
        this.executor = executor;
    }

    /** An audit cannot survive a restart, so anything still queued or running is marked failed. */
    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedAudits() {
        int n = jdbc.update("""
                update website_audit set status = 'FAILED', completed_at = now(),
                    warnings = '["The audit was interrupted by a server restart. Please run it again."]'::jsonb
                where status in ('QUEUED', 'RUNNING')
                """);
        if (n > 0) {
            log.warn("marked {} interrupted audit(s) as failed", n);
        }
    }

    public StartAuditResponse start(UUID owner, UUID profileId, StartAuditRequest request) {
        ProfileResponse profile = profiles.get(owner, profileId); // enforces ownership
        String raw = request.websiteUrl() != null && !request.websiteUrl().isBlank()
                ? request.websiteUrl() : profile.websiteUrl();
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBSITE_URL",
                    "Add a website address to this profile, or enter one, before running an audit.");
        }
        URI uri;
        try {
            uri = guard.validate(raw);
        } catch (UrlRejectedException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBSITE_URL", e.getMessage());
        }
        int maxPages = request.maxPages() == null ? DEFAULT_PAGES : Math.min(request.maxPages(), 10);

        Integer recent = jdbc.queryForObject("""
                select count(*) from website_audit a join growth_profile p on p.id = a.growth_profile_id
                where p.owner_id = ? and a.created_at > now() - interval '1 hour'
                """, Integer.class, owner);
        if (recent != null && recent >= AUDITS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "You have run several audits in the last hour. Please try again later.");
        }
        Integer active = jdbc.queryForObject("""
                select count(*) from website_audit
                where growth_profile_id = ? and status in ('QUEUED', 'RUNNING')
                """, Integer.class, profileId);
        if (active != null && active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "AUDIT_IN_PROGRESS",
                    "An audit for this profile is already running. Wait for it to finish.");
        }

        UUID auditId = jdbc.queryForObject(
                "insert into website_audit (growth_profile_id, website_url, status) values (?, ?, 'QUEUED') returning id",
                UUID.class, profileId, uri.toString());
        List<String> ownerFacts = ownerFacts(profile);
        List<String> products = new ArrayList<>();
        if (profile.businessDetails() != null && profile.businessDetails().productsServices() != null) {
            profile.businessDetails().productsServices().forEach(x -> products.add(x.asString()));
        }
        GeoReadiness.Context geoContext = new GeoReadiness.Context(profile.displayName(), profile.marketLocation(),
                List.copyOf(products));
        try {
            executor.execute(() -> run(owner, auditId, profileId, uri, maxPages, ownerFacts, geoContext));
        } catch (RejectedExecutionException e) {
            fail(auditId, "The audit queue is full. Please try again in a few minutes.", null);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "Many audits are running right now. Please try again in a few minutes.");
        }
        return new StartAuditResponse(auditId, "QUEUED");
    }

    public AuditView get(UUID owner, UUID auditId) {
        record Row(UUID id, UUID profileId, String url, String status, Instant created, Instant started,
                   Instant completed, String pages, String speed, String warnings, String summary,
                   String readiness, String limitations, String crawl, String domain, String geo) {}
        List<Row> rows = jdbc.query("""
                select a.id, a.growth_profile_id, a.website_url, a.status, a.created_at, a.started_at,
                       a.completed_at, a.pages_crawled::text as pages, a.pagespeed_data::text as speed,
                       a.warnings::text as warnings, a.summary, a.readiness_checks::text as readiness,
                       a.limitations::text as limitations, a.crawl_report::text as crawl, a.domain_review::text as domain, a.geo_readiness::text as geo
                from website_audit a join growth_profile p on p.id = a.growth_profile_id
                where a.id = ? and p.owner_id = ?
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("growth_profile_id", UUID.class),
                rs.getString("website_url"), rs.getString("status"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
                rs.getString("pages"), rs.getString("speed"), rs.getString("warnings"), rs.getString("summary"),
                rs.getString("readiness"), rs.getString("limitations"), rs.getString("crawl"), rs.getString("domain"),
                rs.getString("geo")), auditId, owner);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AUDIT_NOT_FOUND", "Audit not found.");
        }
        Row r = rows.get(0);

        List<FindingView> findings = jdbc.query("""
                select id, category, severity, page_url, title, evidence, impact, recommendation,
                       suggested_copy, source_label
                from website_finding where audit_id = ?
                order by case severity when 'HIGH' then 0 when 'MEDIUM' then 1 else 2 end, title
                """, (rs, n) -> new FindingView(rs.getObject("id", UUID.class), rs.getString("category"),
                rs.getString("severity"), rs.getString("page_url"), rs.getString("title"), rs.getString("evidence"),
                rs.getString("impact"), rs.getString("recommendation"), rs.getString("suggested_copy"),
                rs.getString("source_label")), auditId);

        JsonNode readiness = r.readiness() == null ? null : json.readTree(r.readiness());
        List<PageSummary> pages = new ArrayList<>();
        if (r.pages() != null) {
            for (JsonNode p : json.readTree(r.pages())) {
                pages.add(new PageSummary(p.path("url").asString(), p.path("status").asInt(), p.path("title").asString(),
                        p.path("metaDescription").asString(), p.path("wordCount").asInt(), p.path("h1Count").asInt(),
                        p.path("responseMs").asLong(), p.path("truncated").asBoolean()));
            }
        }
        return new AuditView(r.id(), r.profileId(), r.status(), r.url(), r.created(), r.started(), r.completed(),
                pages, r.speed() == null ? null : json.readTree(r.speed()), readiness, readinessSummary(readiness),
                r.summary(), findings, strings(r.limitations()), strings(r.warnings()),
                r.crawl() == null ? null : json.readTree(r.crawl()),
                r.domain() == null ? null : json.readTree(r.domain()),
                r.geo() == null ? null : json.readTree(r.geo()));
    }

    private String evidenceText(UUID auditId) {
        List<String> r = jdbc.query("select evidence_text from website_audit where id = ?",
                (rs, n) -> rs.getString(1), auditId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Every audit for the profile, newest first. Older audits are never overwritten or removed. */
    public List<AuditListItem> list(UUID owner, UUID profileId) {
        profiles.get(owner, profileId);
        return jdbc.query("""
                select a.id, a.status, a.website_url, a.created_at, a.completed_at, a.readiness_checks::text as readiness,
                       a.summary, a.pages_crawled::text as pages, a.pagespeed_data::text as speed,
                       (select count(*) from website_finding f where f.audit_id = a.id and f.severity = 'HIGH') as high,
                       (select count(*) from website_finding f where f.audit_id = a.id and f.severity = 'MEDIUM') as medium,
                       (select count(*) from website_finding f where f.audit_id = a.id and f.severity = 'LOW') as low
                from website_audit a where a.growth_profile_id = ? order by a.created_at desc limit 100
                """, (rs, n) -> {
            String readiness = rs.getString("readiness");
            String pages = rs.getString("pages");
            String speed = rs.getString("speed");
            JsonNode scores = speed == null ? null : json.readTree(speed).path("scores");
            return new AuditListItem(rs.getObject("id", UUID.class), rs.getString("status"),
                    rs.getString("website_url"), instant(rs.getTimestamp("created_at")),
                    instant(rs.getTimestamp("completed_at")),
                    readiness == null ? null : readinessSummary(json.readTree(readiness)), rs.getString("summary"),
                    pages == null ? 0 : json.readTree(pages).size(),
                    new SeverityCounts(rs.getInt("high"), rs.getInt("medium"), rs.getInt("low")),
                    scores != null && scores.hasNonNull("performance") ? scores.get("performance").asInt() : null,
                    scores != null && scores.hasNonNull("seo") ? scores.get("seo").asInt() : null);
        }, profileId);
    }

    /**
     * Compares two audits of the same profile (default: the latest two finished audits). Check results
     * are exact. A finding counts as resolved when its quoted evidence no longer appears in the later
     * audit; that shows the problem is gone, not which change removed it.
     */
    public AuditComparison compare(UUID owner, UUID profileId, UUID fromId, UUID toId) {
        profiles.get(owner, profileId);
        if (fromId == null || toId == null) {
            List<UUID> latest = jdbc.query("""
                    select id from website_audit where growth_profile_id = ? and status in ('COMPLETED', 'PARTIAL')
                    order by created_at desc limit 2
                    """, (rs, n) -> rs.getObject("id", UUID.class), profileId);
            if (latest.size() < 2) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_AUDITS",
                        "Run at least two audits before comparing. The second one shows what changed.");
            }
            toId = latest.get(0);
            fromId = latest.get(1);
        }
        AuditView from = get(owner, fromId);
        AuditView to = get(owner, toId);
        if (!from.profileId().equals(profileId) || !to.profileId().equals(profileId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AUDIT_NOT_FOUND", "Audit not found.");
        }
        if (from.createdAt().isAfter(to.createdAt())) {
            AuditView t = from;
            from = to;
            to = t;
        }
        return AuditComparison.build(from, to, evidenceText(from.auditId()), evidenceText(to.auditId()));
    }

    // ---- background work -------------------------------------------------------------------

    /** What the owner told us about the business, as plain lines for the evidence. */
    private static List<String> ownerFacts(ProfileResponse profile) {
        List<String> facts = new ArrayList<>();
        addFact(facts, "business name", profile.displayName());
        addFact(facts, "audience", profile.targetAudience());
        addFact(facts, "market", profile.marketLocation());
        addFact(facts, "goal", profile.primaryGoal());
        if (profile.businessDetails() != null) {
            addFact(facts, "category", profile.businessDetails().category());
            addFact(facts, "description", profile.businessDetails().description());
            JsonNode products = profile.businessDetails().productsServices();
            if (products != null && products.isArray() && !products.isEmpty()) {
                List<String> names = new ArrayList<>();
                products.forEach(p -> names.add(p.asString()));
                addFact(facts, "products or services", String.join(", ", names));
            }
        }
        return List.copyOf(facts);
    }

    private static void addFact(List<String> facts, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(label + ": " + value.trim());
        }
    }

    private void run(UUID owner, UUID auditId, UUID profileId, URI uri, int maxPages, List<String> ownerFacts,
                     GeoReadiness.Context geoContext) {
        try {
            jdbc.update("update website_audit set status = 'RUNNING', started_at = now() where id = ?", auditId);

            // PageSpeed is slow (often 10 to 30 seconds), so it runs while the crawl runs.
            CompletableFuture<PageSpeedClient.Result> speedFuture = CompletableFuture
                    .supplyAsync(() -> pageSpeedFor(profileId, uri), Thread::startVirtualThread)
                    .exceptionally(e -> {
                        log.warn("PageSpeed step failed: {}", e.getClass().getSimpleName());
                        return new PageSpeedClient.Result(false, "mobile", java.util.Map.of(), java.util.Map.of(),
                                List.of(), null, null, "PageSpeed could not be run.");
                    });

            WebsiteCrawler.Result crawl;
            try {
                crawl = crawler.crawl(uri, WebsiteCrawler.Options.of(maxPages));
            } catch (CrawlFailedException e) {
                fail(auditId, e.getMessage(), speedFuture.join());
                return;
            }
            PageSpeedClient.Result speed = speedFuture.join();

            List<Check> checks = ReadinessChecks.evaluate(crawl.pages().get(0), crawl.startUri(), speed);
            Evidence evidence = AuditEvidence.build(crawl, checks, speed, maxPages, ownerFacts);

            List<String> warnings = new ArrayList<>(crawl.warnings());
            if (!speed.available()) {
                warnings.add("PageSpeed is unavailable: " + speed.error());
            }

            Output output = null;
            try {
                output = findings(profileId, evidence);
            } catch (ApiException e) {
                warnings.add("The written findings are unavailable right now (" + e.getMessage()
                        + ") The crawl, PageSpeed results and readiness checks are still accurate.");
            }
            if (output != null && output.dropped() > 0) {
                warnings.add(output.dropped() + " finding(s) were removed because their evidence could not be "
                        + "verified against the audited pages.");
            }

            java.util.Map<String, Object> domainSection = domainSection(owner, profileId, uri, crawl, warnings);
            List<GeoReadiness.Signal> geo = GeoReadiness.evaluate(crawl, geoContext);
            java.util.Map<String, Object> geoSection = new java.util.LinkedHashMap<>();
            geoSection.put("signals", geo);
            geoSection.put("summary", GeoReadiness.summary(geo));
            geoSection.put("aiSearchTests", "NOT_TESTED");

            String status = output != null && speed.available() ? "COMPLETED" : "PARTIAL";
            save(auditId, status, crawl, speed, checks, output, warnings, evidence.text(), domainSection, geoSection);
        } catch (Throwable t) {
            log.error("audit {} failed unexpectedly: {}", auditId, t.getClass().getSimpleName(), t);
            fail(auditId, "The audit stopped unexpectedly. Please try again.", null);
        }
    }

    private Output findings(UUID profileId, Evidence evidence) {
        ClaudeRequest request = new ClaudeRequest("website_audit", profileId,
                List.of("common_rules", "website_audit"),
                "Audit the website described in the EVIDENCE below.\n\nEVIDENCE:\n" + evidence.text(),
                Model.CLAUDE_SONNET_5_5, 16000, OutputConfig.Effort.MEDIUM,
                AiSchemas.format(AiSchemas.WEBSITE_AUDIT));
        // First attempt is strict, so the model is told exactly which finding failed verification.
        // If it fails again, unverifiable findings are dropped and counted rather than shown.
        AtomicInteger attempt = new AtomicInteger();
        return claude.completeValidated(request, text -> AuditOutputParser.parse(json, text, evidence.pageUrls(),
                evidence.text(), attempt.incrementAndGet() == 1));
    }

    /** Reuses a PageSpeed result for the same address from the last six hours, to save quota. */
    private PageSpeedClient.Result pageSpeedFor(UUID profileId, URI uri) {
        List<String> recent = jdbc.query("""
                select pagespeed_data::text from website_audit
                where growth_profile_id = ? and website_url = ? and pagespeed_data is not null
                  and pagespeed_data->>'available' = 'true' and created_at > now() - interval '6 hours'
                order by created_at desc limit 1
                """, (rs, n) -> rs.getString(1), profileId, uri.toString());
        if (!recent.isEmpty()) {
            try {
                return json.readValue(recent.get(0), PageSpeedClient.Result.class);
            } catch (RuntimeException e) {
                log.warn("could not reuse stored PageSpeed data");
            }
        }
        return pageSpeed.analyse(uri.toString());
    }

    /**
     * Measured domain facts plus the written assessment. The written assessment costs one AI call, so a
     * review made for the same domain in the last seven days is reused instead of asking again.
     */
    private java.util.Map<String, Object> domainSection(UUID owner, UUID profileId, URI entered,
                                                        WebsiteCrawler.Result crawl, List<String> warnings) {
        String host = entered.getHost() == null ? null : entered.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host == null) {
            return null;
        }
        URI landed = entered;
        try {
            if (!crawl.pages().isEmpty()) {
                landed = URI.create(crawl.pages().get(0).url());
            }
        } catch (IllegalArgumentException ignored) {
            // keep the entered address
        }
        List<DomainHealth.Fact> facts = DomainHealth.measure(entered, landed, fetcher);
        java.util.Map<String, Object> section = new java.util.LinkedHashMap<>();
        section.put("host", host);
        section.put("facts", facts);
        section.put("aiReview", null);
        if (!host.contains(".") || !host.matches(".*[a-z].*")) {
            return section;
        }
        List<String> previous = jdbc.query("""
                select domain_review::text from website_audit
                where growth_profile_id = ? and domain_review->>'host' = ?
                  and created_at > now() - interval '7 days'
                  and jsonb_typeof(domain_review->'aiReview') = 'object'
                order by created_at desc limit 1
                """, (rs, n) -> rs.getString(1), profileId, host);
        if (!previous.isEmpty()) {
            section.put("aiReview", json.readTree(previous.get(0)).get("aiReview"));
            section.put("aiReviewReused", true);
            return section;
        }
        try {
            JsonNode siteFacts = json.readTree(json.writeValueAsString(facts));
            FitResponse fit = domainFit.assessWith(owner, profileId, List.of(host), false, host, siteFacts);
            warnings.addAll(fit.warnings());
            java.util.Map<String, Object> ai = new java.util.LinkedHashMap<>();
            if (fit.currentDomainReview() != null) {
                ai.put("recommendation", fit.currentDomainReview().recommendation());
                ai.put("reasoning", fit.currentDomainReview().reasoning());
                ai.put("risks", fit.currentDomainReview().risks());
                ai.put("checklist", fit.currentDomainReview().checklist());
            }
            if (!fit.items().isEmpty() && fit.items().get(0).assessment() != null) {
                var a = fit.items().get(0).assessment();
                ai.put("verdict", a.verdict());
                ai.put("strengths", a.strengths());
                ai.put("concerns", a.concerns());
                ai.put("suggestion", a.suggestion());
            }
            section.put("aiReview", ai.isEmpty() ? null : ai);
        } catch (RuntimeException e) {
            warnings.add("The written domain assessment is unavailable right now. The measured domain facts are still accurate.");
        }
        return section;
    }

    /** What was read, what was left out and why, in words a business owner can act on. */
    static java.util.Map<String, Object> crawlReport(WebsiteCrawler.Result crawl) {
        long robots = crawl.skipped().stream().filter(x -> "ROBOTS_DISALLOWED".equals(x.reason())).count();
        List<String> impact = new ArrayList<>();
        if (robots > 0) {
            impact.add(robots + " page(s) were left out because the site's robots.txt asks automated tools not to "
                    + "read them. We respect that rule. The homepage checks and the findings cover only the pages "
                    + "that were read; the skipped pages may have issues this audit cannot show.");
        }
        long failed = crawl.skipped().stream().filter(x -> !"ROBOTS_DISALLOWED".equals(x.reason())).count();
        if (failed > 0) {
            impact.add(failed + " page(s) could not be read (see the reasons below), so they are not covered.");
        }
        if (crawl.notVisited() > 0) {
            impact.add(crawl.notVisited() + " more linked page(s) were found but not read because the page or time "
                    + "limit was reached first. Run a larger audit to cover more of the site.");
        }
        if (impact.isEmpty()) {
            impact.add("Every page the crawler found was read.");
        }
        java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("robotsTxtFound", crawl.robotsFound());
        report.put("pagesRead", crawl.pages().size());
        report.put("skipped", crawl.skipped());
        report.put("notVisited", crawl.notVisited());
        report.put("impact", impact);
        report.put("complete", robots == 0 && failed == 0 && crawl.notVisited() == 0);
        return report;
    }

    private void save(UUID auditId, String status, WebsiteCrawler.Result crawl, PageSpeedClient.Result speed,
                      List<Check> checks, Output output, List<String> warnings, String evidenceText,
                      java.util.Map<String, Object> domainSection, java.util.Map<String, Object> geoSection) {
        List<PageSummary> pages = crawl.pages().stream().map(p -> new PageSummary(p.url(), p.status(), p.title(),
                p.metaDescription(), p.wordCount(), p.h1().size(), p.responseMs(), p.truncated())).toList();

        jdbc.update("""
                update website_audit set status = ?, completed_at = now(), summary = ?,
                    pages_crawled = ?::jsonb, pagespeed_data = ?::jsonb, readiness_checks = ?::jsonb,
                    warnings = ?::jsonb, limitations = ?::jsonb, crawl_report = ?::jsonb,
                    evidence_text = ?, domain_review = ?::jsonb, geo_readiness = ?::jsonb
                where id = ?
                """, status, output == null ? null : output.summary(), json.writeValueAsString(pages),
                json.writeValueAsString(speed), json.writeValueAsString(checks), json.writeValueAsString(warnings),
                json.writeValueAsString(output == null ? List.of() : output.limitations()),
                json.writeValueAsString(crawlReport(crawl)), evidenceText,
                json.writeValueAsString(domainSection), json.writeValueAsString(geoSection), auditId);

        if (output != null) {
            for (Finding f : output.findings()) {
                jdbc.update("""
                        insert into website_finding (audit_id, category, severity, page_url, title, evidence,
                            impact, recommendation, suggested_copy, source_label)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, auditId, f.category(), f.severity(), f.pageUrl(), f.title(), f.evidence(), f.impact(),
                        f.recommendation(), f.suggestedCopy().isEmpty() ? null : f.suggestedCopy(),
                        "OBSERVED".equals(f.basis()) ? "OBSERVED_PUBLIC_DATA" : "AI_DERIVED");
            }
        }
    }

    private void fail(UUID auditId, String message, PageSpeedClient.Result speed) {
        jdbc.update("""
                update website_audit set status = 'FAILED', completed_at = now(), warnings = ?::jsonb,
                    pagespeed_data = ?::jsonb
                where id = ?
                """, json.writeValueAsString(List.of(message)),
                speed == null ? null : json.writeValueAsString(speed), auditId);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private List<String> strings(String jsonArray) {
        List<String> out = new ArrayList<>();
        if (jsonArray != null) {
            for (JsonNode n : json.readTree(jsonArray)) {
                out.add(n.asString());
            }
        }
        return out;
    }

    private static String readinessSummary(JsonNode checks) {
        if (checks == null || !checks.isArray()) {
            return null;
        }
        int assessed = 0;
        int passed = 0;
        for (JsonNode c : checks) {
            JsonNode p = c.get("passed");
            if (p != null && !p.isNull()) {
                assessed++;
                if (p.asBoolean()) {
                    passed++;
                }
            }
        }
        int unassessed = checks.size() - assessed;
        return passed + " of " + assessed + " checks passed"
                + (unassessed > 0 ? " (" + unassessed + " could not be assessed)" : "");
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
