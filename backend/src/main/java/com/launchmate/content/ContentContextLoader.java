package com.launchmate.content;

import com.launchmate.content.ContentEvidence.ChannelContext;
import com.launchmate.content.ContentEvidence.Site;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads the owner's saved website audit and channel analysis for use as writing context. */
@Component
class ContentContextLoader {

    private static final int MAX_PAGES = 5;
    private static final int MAX_PROBLEMS = 5;
    private static final int MAX_TITLES = 25;

    /** The wording of one crawled page, used as "current text" for meta tags. */
    record PageText(String url, String title, String description) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    ContentContextLoader(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record SiteContext(Site site, List<PageText> pages) {}

    SiteContext site(UUID profileId) {
        record Row(UUID id, String url, Instant completed, String pages) {}
        List<Row> rows = jdbc.query("""
                select id, website_url, completed_at, pages_crawled::text as pages from website_audit
                where growth_profile_id = ? and status in ('COMPLETED', 'PARTIAL') and pages_crawled is not null
                order by created_at desc limit 1
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("website_url"),
                instant(rs.getTimestamp("completed_at")), rs.getString("pages")), profileId);
        if (rows.isEmpty()) {
            return null;
        }
        Row r = rows.get(0);
        List<PageText> pages = new ArrayList<>();
        for (JsonNode p : json.readTree(r.pages())) {
            pages.add(new PageText(p.path("url").asString(), p.path("title").asString(), p.path("metaDescription").asString()));
        }
        if (pages.isEmpty()) {
            return null;
        }
        List<String> problems = jdbc.query("""
                select title from website_finding where audit_id = ?
                order by case severity when 'HIGH' then 0 when 'MEDIUM' then 1 else 2 end, title limit ?
                """, (rs, n) -> rs.getString(1), r.id(), MAX_PROBLEMS);
        List<String> titles = pages.stream().limit(MAX_PAGES).map(PageText::title).filter(t -> !t.isBlank()).toList();
        PageText home = pages.get(0);
        return new SiteContext(new Site(r.url(), r.completed(), home.title(), home.description(), titles, problems), pages);
    }

    ChannelContext channel(UUID profileId) {
        record Row(UUID id, String stats, String analysis) {}
        List<Row> rows = jdbc.query("""
                select id, channel_stats::text as stats, analysis::text as analysis from channel_analysis
                where growth_profile_id = ? and status in ('COMPLETED', 'PARTIAL') and channel_stats is not null
                order by created_at desc limit 1
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("stats"), rs.getString("analysis")),
                profileId);
        if (rows.isEmpty()) {
            return null;
        }
        Row r = rows.get(0);
        JsonNode snapshot = json.readTree(r.stats());
        JsonNode channel = snapshot.path("channel");
        JsonNode stats = snapshot.path("stats");

        List<String> titles = jdbc.query("""
                select title from channel_video where analysis_id = ? order by published_at desc limit ?
                """, (rs, n) -> rs.getString(1), r.id(), MAX_TITLES);

        JsonNode t = stats.path("titles");
        List<String> style = new ArrayList<>();
        style.add("title style: average " + t.path("averageLength").asString() + " characters; numbers in "
                + t.path("withNumber").asInt() + " of " + t.path("titles").asInt() + "; question marks in "
                + t.path("withQuestion").asInt() + "; emoji in " + t.path("withEmoji").asInt());
        List<String> words = new ArrayList<>();
        t.path("topWords").forEach(w -> words.add(w.path("word").asString() + " (" + w.path("count").asInt() + ")"));
        if (!words.isEmpty()) {
            style.add("most repeated words in titles: " + String.join(", ", words));
        }

        List<String> themes = new ArrayList<>();
        if (r.analysis() != null) {
            json.readTree(r.analysis()).path("themes").forEach(th -> themes.add(th.path("name").asString()));
        }
        List<String> best = new ArrayList<>();
        stats.path("best").forEach(b -> best.add(b.path("title").asString()));

        String name = channel.path("title").asString()
                + (channel.hasNonNull("handle") ? " (" + channel.get("handle").asString() + ")" : "");
        return new ChannelContext(name, titles, List.copyOf(style), List.copyOf(themes), List.copyOf(best));
    }

    /** Title and description of a crawled page, for meta tags: the homepage when no page is named. */
    static String originalMeta(SiteContext site, String pageUrl) {
        if (site == null) {
            return null;
        }
        PageText page = null;
        if (pageUrl == null || pageUrl.isBlank()) {
            page = site.pages().get(0);
        } else {
            for (PageText p : site.pages()) {
                if (strip(p.url()).equalsIgnoreCase(strip(pageUrl))) {
                    page = p;
                    break;
                }
            }
        }
        if (page == null) {
            return null;
        }
        return "title: " + (page.title().isBlank() ? "(none)" : page.title()) + "\ndescription: "
                + (page.description().isBlank() ? "(none)" : page.description());
    }

    private static String strip(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
