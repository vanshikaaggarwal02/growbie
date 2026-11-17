package com.launchmate.growth;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.launchmate.ai.AiSchemas;
import com.launchmate.ai.ClaudeService;
import com.launchmate.ai.ClaudeService.ClaudeRequest;
import com.launchmate.common.ApiException;
import com.launchmate.growth.GrowthPlanDtos.PlanListItem;
import com.launchmate.growth.GrowthPlanDtos.PlanView;
import com.launchmate.growth.GrowthPlanDtos.Progress;
import com.launchmate.growth.GrowthPlanDtos.RecommendationView;
import com.launchmate.growth.GrowthPlanDtos.StartPlanResponse;
import com.launchmate.growth.GrowthPlanDtos.UpdateRecommendationRequest;
import com.launchmate.growth.PlanEvidence.Evidence;
import com.launchmate.growth.PlanOutputParser.Output;
import com.launchmate.growth.PlanOutputParser.Rec;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Builds a prioritised growth plan from the owner's saved results and tracks what the owner does with
 * each recommendation. The plan is generated in the background; its recommendations are verified
 * against the evidence, ranked by a fixed impact-and-effort rule, and saved.
 */
@Service
public class GrowthPlanService {

    private static final Logger log = LoggerFactory.getLogger(GrowthPlanService.class);
    private static final int PLANS_PER_HOUR = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProfileService profiles;
    private final PlanInputLoader loader;
    private final ClaudeService claude;
    private final ThreadPoolExecutor executor;

    public GrowthPlanService(JdbcTemplate jdbc, ObjectMapper json, ProfileService profiles, PlanInputLoader loader,
                             ClaudeService claude, @Qualifier("analysisExecutor") ThreadPoolExecutor executor) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.loader = loader;
        this.claude = claude;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedPlans() {
        int n = jdbc.update("""
                update growth_plan set status = 'FAILED', completed_at = now(),
                    warnings = '["The plan was interrupted by a server restart. Please generate it again."]'::jsonb
                where status in ('QUEUED', 'RUNNING')
                """);
        if (n > 0) {
            log.warn("marked {} interrupted growth plan(s) as failed", n);
        }
    }

    public StartPlanResponse start(UUID owner, UUID profileId) {
        ProfileResponse profile = profiles.get(owner, profileId); // enforces ownership
        PlanInputs inputs = loader.load(profile);
        if (!inputs.hasAnalysis() && !"NEW_BUSINESS".equals(profile.profileType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANALYSIS_NOT_READY",
                    "Run a website audit or a channel analysis first, so the plan has results to work from.");
        }
        Integer recent = jdbc.queryForObject("""
                select count(*) from growth_plan g join growth_profile p on p.id = g.growth_profile_id
                where p.owner_id = ? and g.created_at > now() - interval '1 hour'
                """, Integer.class, owner);
        if (recent != null && recent >= PLANS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "You have generated several plans in the last hour. Please try again later.");
        }
        Integer active = jdbc.queryForObject("""
                select count(*) from growth_plan where growth_profile_id = ? and status in ('QUEUED', 'RUNNING')
                """, Integer.class, profileId);
        if (active != null && active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_IN_PROGRESS",
                    "A plan for this profile is already being generated. Wait for it to finish.");
        }

        UUID planId = jdbc.queryForObject(
                "insert into growth_plan (growth_profile_id, status) values (?, 'QUEUED') returning id", UUID.class, profileId);
        try {
            executor.execute(() -> run(planId, profileId, inputs));
        } catch (RejectedExecutionException e) {
            fail(planId, "The queue is full. Please try again in a few minutes.");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWL_LIMIT_EXCEEDED",
                    "Many jobs are running right now. Please try again in a few minutes.");
        }
        return new StartPlanResponse(planId, "QUEUED");
    }

    public PlanView get(UUID owner, UUID planId) {
        record Row(UUID id, UUID profileId, String status, Instant created, Instant started, Instant completed,
                   String summary, String limitations, String warnings, String inputs) {}
        List<Row> rows = jdbc.query("""
                select g.id, g.growth_profile_id, g.status, g.created_at, g.started_at, g.completed_at, g.summary,
                       g.limitations::text as limitations, g.warnings::text as warnings, g.inputs::text as inputs
                from growth_plan g join growth_profile p on p.id = g.growth_profile_id
                where g.id = ? and p.owner_id = ?
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("growth_profile_id", UUID.class),
                rs.getString("status"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")), rs.getString("summary"), rs.getString("limitations"),
                rs.getString("warnings"), rs.getString("inputs")), planId, owner);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found.");
        }
        Row r = rows.get(0);
        List<RecommendationView> recs = recommendations(planId);
        return new PlanView(r.id(), r.profileId(), r.status(), r.created(), r.started(), r.completed(), r.summary(),
                recs, progress(recs), strings(r.limitations()), strings(r.warnings()),
                r.inputs() == null ? null : json.readTree(r.inputs()));
    }

    public List<PlanListItem> list(UUID owner, UUID profileId) {
        record ListItemRow(UUID id, String status, Instant created, String summary) {}
        profiles.get(owner, profileId);
        List<ListItemRow> plans = jdbc.query("""
                select id, status, created_at, summary from growth_plan where growth_profile_id = ?
                order by created_at desc limit 20
                """, (rs, n) -> new ListItemRow(rs.getObject("id", UUID.class), rs.getString("status"),
                instant(rs.getTimestamp("created_at")), rs.getString("summary")), profileId);
        List<PlanListItem> out = new ArrayList<>();
        for (ListItemRow p : plans) {
            out.add(new PlanListItem(p.id(), p.status(), p.created(), p.summary(), progress(recommendations(p.id()))));
        }
        return out;
    }

    public RecommendationView update(UUID owner, UUID recommendationId, UpdateRecommendationRequest request) {
        boolean noteGiven = request.note() != null;
        String note = noteGiven && !request.note().isBlank() ? request.note().trim() : null;
        int changed = jdbc.update("""
                update recommendation r set status = ?, note = case when ? then ? else r.note end,
                    completed_at = case when ? = 'COMPLETED' then now() else null end, updated_at = now()
                where r.id = ? and r.plan_id in (
                    select g.id from growth_plan g join growth_profile p on p.id = g.growth_profile_id
                    where p.owner_id = ?)
                """, request.status(), noteGiven, note, request.status(), recommendationId, owner);
        if (changed == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "Recommendation not found.");
        }
        return jdbc.query(RECOMMENDATION_SQL + " where r.id = ?", this::map, recommendationId).get(0);
    }

    // ---- background work -------------------------------------------------------------------

    private void run(UUID planId, UUID profileId, PlanInputs inputs) {
        try {
            jdbc.update("update growth_plan set status = 'RUNNING', started_at = now() where id = ?", planId);
            Evidence evidence = PlanEvidence.build(inputs);

            Output output;
            try {
                output = generate(profileId, evidence);
            } catch (ApiException e) {
                fail(planId, "The plan could not be written right now (" + e.getMessage() + ")");
                return;
            }

            List<String> warnings = new ArrayList<>();
            if (output.dropped() > 0) {
                warnings.add(output.dropped() + " recommendation(s) were removed because their evidence could not be "
                        + "verified against your saved results.");
            }
            save(planId, inputs, output, warnings);
        } catch (Throwable t) {
            log.error("growth plan {} failed unexpectedly: {}", planId, t.getClass().getSimpleName(), t);
            fail(planId, "The plan stopped unexpectedly. Please try again.");
        }
    }

    private Output generate(UUID profileId, Evidence evidence) {
        ClaudeRequest request = new ClaudeRequest("growth_plan", profileId, List.of("common_rules", "growth_plan"),
                "Write the growth plan for the project described in the EVIDENCE below.\n\nEVIDENCE:\n" + evidence.text(),
                Model.CLAUDE_SONNET_5_5, 16000, OutputConfig.Effort.MEDIUM, AiSchemas.format(AiSchemas.GROWTH_PLAN));
        AtomicInteger attempt = new AtomicInteger();
        return claude.completeValidated(request, text -> PlanOutputParser.parse(json, text, evidence.ids(),
                evidence.text(), attempt.incrementAndGet() == 1));
    }

    private void save(UUID planId, PlanInputs inputs, Output output, List<String> warnings) {
        record Ranked(Rec rec, String priority) {}
        List<Ranked> ranked = new ArrayList<>();
        for (Rec rec : output.recommendations()) {
            ranked.add(new Ranked(rec, PriorityMatrix.priority(rec.impact(), rec.effort())));
        }
        // A stable sort: ties keep the order the model wrote them in.
        ranked.sort(Comparator.comparingInt(r -> PriorityMatrix.rank(r.priority(), r.rec().impact(), r.rec().effort())));

        int position = 0;
        for (Ranked r : ranked) {
            Rec rec = r.rec();
            jdbc.update("""
                    insert into recommendation (plan_id, module, priority, impact, effort, title, evidence, rationale,
                        suggested_action, how_to_measure, source_ids, source_label, position, confidence)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    """, planId, rec.module(), r.priority(), rec.impact(), rec.effort(), rec.title(), rec.evidence(),
                    rec.rationale(), rec.suggestedAction(), rec.howToMeasure(), json.writeValueAsString(rec.sourceIds()),
                    switch (rec.basis()) {
                        case "OBSERVED" -> "OBSERVED_PUBLIC_DATA";
                        case "USER_PROVIDED" -> "USER_PROVIDED";
                        default -> "AI_DERIVED";
                    }, ++position, switch (rec.basis()) {
                        // How solid the evidence is, not a forecast: measured facts rate higher than inferences.
                        case "OBSERVED" -> "HIGH";
                        case "USER_PROVIDED" -> "MEDIUM";
                        default -> "LOW";
                    });
        }

        Map<String, Object> used = new LinkedHashMap<>();
        used.put("websiteAudit", inputs.audit() == null ? null : inputs.audit().id());
        used.put("channelAnalysis", inputs.channel() == null ? null : inputs.channel().id());
        used.put("domainsConsidered", inputs.domains().size());
        used.put("brandNamesConsidered", inputs.brandNames().size());
        used.put("previousProgressItems", inputs.progress().size());

        jdbc.update("""
                update growth_plan set status = 'COMPLETED', completed_at = now(), summary = ?, inputs = ?::jsonb,
                    limitations = ?::jsonb, warnings = ?::jsonb
                where id = ?
                """, output.summary(), json.writeValueAsString(used), json.writeValueAsString(output.limitations()),
                json.writeValueAsString(warnings), planId);
    }

    private void fail(UUID planId, String message) {
        jdbc.update("update growth_plan set status = 'FAILED', completed_at = now(), warnings = ?::jsonb where id = ?",
                json.writeValueAsString(List.of(message)), planId);
    }

    // ---- reading ----------------------------------------------------------------------------

    private static final String RECOMMENDATION_SQL = """
            select r.id, r.position, r.module, r.priority, r.impact, r.effort, r.status, r.title, r.evidence,
                   r.rationale, r.suggested_action, r.how_to_measure, r.source_ids::text as source_ids,
                   r.source_label, r.note, r.completed_at, r.updated_at, r.confidence
            from recommendation r
            """;

    private List<RecommendationView> recommendations(UUID planId) {
        return jdbc.query(RECOMMENDATION_SQL + " where r.plan_id = ? order by r.position", this::map, planId);
    }

    private RecommendationView map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new RecommendationView(rs.getObject("id", UUID.class), rs.getInt("position"), rs.getString("module"),
                rs.getString("priority"), rs.getString("impact"), rs.getString("effort"), rs.getString("status"),
                rs.getString("title"), rs.getString("evidence"), rs.getString("rationale"),
                rs.getString("suggested_action"), rs.getString("how_to_measure"), strings(rs.getString("source_ids")),
                rs.getString("source_label"), rs.getString("note"), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("updated_at")), rs.getString("confidence"));
    }

    private static Progress progress(List<RecommendationView> recs) {
        int open = 0;
        int inProgress = 0;
        int completed = 0;
        int dismissed = 0;
        for (RecommendationView r : recs) {
            switch (r.status()) {
                case "OPEN" -> open++;
                case "IN_PROGRESS" -> inProgress++;
                case "COMPLETED" -> completed++;
                default -> dismissed++;
            }
        }
        return new Progress(recs.size(), open, inProgress, completed, dismissed);
    }

    private List<String> strings(String jsonArray) {
        List<String> out = new ArrayList<>();
        if (jsonArray != null) {
            for (JsonNode n : json.readTree(jsonArray)) {
                out.add(n.asString());
            }
        }
        return out;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
