package com.launchmate.website;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GEO readiness: signals on the business's own website that affect whether AI search tools can read
 * and accurately describe it. Every signal is measured or explicitly marked as not analysed or
 * unavailable. There is deliberately no score: no ranking formula for AI search is known, and AI
 * search tests are reported separately and never inferred from these signals.
 */
public final class GeoReadiness {

    /** status is PASS, FAIL, NOT_ANALYSED or UNAVAILABLE. */
    public record Signal(String key, String label, String status, String detail, String source) {}

    /** What the owner told us, used to check whether the site says the same things. */
    public record Context(String businessName, String location, List<String> products) {}

    static final String OBSERVED = "OBSERVED_PUBLIC_DATA";
    private static final int THIN_WORDS = 120;

    private GeoReadiness() {}

    public static List<Signal> evaluate(WebsiteCrawler.Result crawl, Context ctx) {
        List<Signal> s = new ArrayList<>();
        PageData home = crawl.pages().get(0);

        if (!crawl.robotsFound()) {
            s.add(new Signal("AI_CRAWLERS", "AI crawlers are allowed to read the site", "PASS",
                    "There is no robots.txt, so nothing is blocked.", OBSERVED));
        } else if (crawl.aiCrawlersBlocked().isEmpty()) {
            s.add(new Signal("AI_CRAWLERS", "AI crawlers are allowed to read the site", "PASS",
                    "robots.txt does not block the AI crawlers we checked (GPTBot, ClaudeBot, PerplexityBot, "
                            + "Google-Extended, CCBot).", OBSERVED));
        } else {
            s.add(new Signal("AI_CRAWLERS", "AI crawlers are allowed to read the site", "FAIL",
                    "robots.txt blocks: " + String.join(", ", crawl.aiCrawlersBlocked())
                            + ". That may be intentional; if you want to be described by those tools, it would need to change.",
                    OBSERVED));
        }

        Integer sitemap = crawl.sitemapEntries();
        s.add(sitemap == null
                ? new Signal("SITEMAP", "A sitemap is published", "UNAVAILABLE", "The sitemap could not be checked.", OBSERVED)
                : sitemap > 0
                ? new Signal("SITEMAP", "A sitemap is published", "PASS", "sitemap.xml lists " + sitemap + " address(es).", OBSERVED)
                : new Signal("SITEMAP", "A sitemap is published", "FAIL", "No sitemap.xml was found at the usual address.", OBSERVED));

        long withData = crawl.pages().stream().filter(PageData::structuredData).count();
        s.add(withData > 0
                ? new Signal("STRUCTURED_DATA", "Structured data describes the business", "PASS",
                        "Structured data (JSON-LD) found on " + withData + " of " + crawl.pages().size()
                                + " page(s) read. Whether it is complete and correct is not assessed.", OBSERVED)
                : new Signal("STRUCTURED_DATA", "Structured data describes the business", "FAIL",
                        "No structured data (JSON-LD) was found on the " + crawl.pages().size()
                                + " page(s) read, so nothing states in machine-readable form what the business is.",
                        OBSERVED));

        String homeText = norm(home.title() + " " + String.join(" ", home.h1()) + " " + home.metaDescription() + " " + home.text());
        String name = norm(ctx.businessName());
        s.add(name.isEmpty()
                ? new Signal("NAME_ON_SITE", "The business name is on the homepage", "UNAVAILABLE", "No business name was given.", "USER_PROVIDED")
                : homeText.contains(name)
                ? new Signal("NAME_ON_SITE", "The business name is on the homepage", "PASS",
                        "\"" + ctx.businessName() + "\" appears in the homepage title, heading or text.", OBSERVED)
                : new Signal("NAME_ON_SITE", "The business name is on the homepage", "FAIL",
                        "\"" + ctx.businessName() + "\" does not appear in the homepage title, heading or text.", OBSERVED));

        List<String> products = ctx.products() == null ? List.of() : ctx.products();
        if (products.isEmpty()) {
            s.add(new Signal("OFFER_ON_SITE", "The homepage says what you sell", "UNAVAILABLE",
                    "No products or services were given, so there is nothing to look for.", "USER_PROVIDED"));
        } else {
            List<String> found = products.stream().filter(p -> !norm(p).isEmpty() && homeText.contains(norm(p))).toList();
            s.add(found.isEmpty()
                    ? new Signal("OFFER_ON_SITE", "The homepage says what you sell", "FAIL",
                            "None of the products or services you listed (" + String.join(", ", products)
                                    + ") appear on the homepage.", OBSERVED)
                    : new Signal("OFFER_ON_SITE", "The homepage says what you sell", "PASS",
                            "Mentioned on the homepage: " + String.join(", ", found) + ".", OBSERVED));
        }

        String place = ctx.location() == null ? "" : norm(ctx.location().split(",")[0]);
        boolean notAPlace = java.util.Set.of("global", "worldwide", "online", "international", "everywhere", "anywhere")
                .contains(place);
        s.add(notAPlace
                ? new Signal("LOCATION_ON_SITE", "The location is clear", "UNAVAILABLE",
                        "Your market isn't a single place, so there is no location to look for.", "USER_PROVIDED")
                : place.isEmpty()
                ? new Signal("LOCATION_ON_SITE", "The location is clear", "UNAVAILABLE", "No location was given.", "USER_PROVIDED")
                : homeText.contains(place)
                ? new Signal("LOCATION_ON_SITE", "The location is clear", "PASS",
                        "\"" + ctx.location().split(",")[0].trim() + "\" appears on the homepage.", OBSERVED)
                : new Signal("LOCATION_ON_SITE", "The location is clear", "FAIL",
                        "\"" + ctx.location().split(",")[0].trim() + "\" does not appear in the homepage title, heading or text.",
                        OBSERVED));

        List<PageData> others = crawl.pages().stream().skip(1).toList();
        if (others.isEmpty()) {
            s.add(new Signal("THIN_PAGES", "Pages have enough text to be useful", "UNAVAILABLE",
                    "Only the homepage was read.", OBSERVED));
        } else {
            long thin = others.stream().filter(p -> p.wordCount() < THIN_WORDS).count();
            s.add(thin == 0
                    ? new Signal("THIN_PAGES", "Pages have enough text to be useful", "PASS",
                            "All " + others.size() + " other page(s) read have at least " + THIN_WORDS + " words.", OBSERVED)
                    : new Signal("THIN_PAGES", "Pages have enough text to be useful", "FAIL",
                            thin + " of " + others.size() + " other page(s) read have fewer than " + THIN_WORDS
                                    + " words. Short pages leave little for an AI tool to describe accurately.", OBSERVED));
        }

        s.add(new Signal("CONSISTENCY", "Details match across your profiles", "NOT_ANALYSED",
                "Comparing your website with your Google Business Profile and other listings needs access we don't have yet.", "UNAVAILABLE"));
        s.add(new Signal("MENTIONS", "Credible mentions on other sites", "UNAVAILABLE",
                "Mentions elsewhere can't be verified automatically, so we don't guess.", "UNAVAILABLE"));
        return s;
    }

    private static String norm(String t) {
        return t == null ? "" : t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public static String summary(List<Signal> signals) {
        long assessed = signals.stream().filter(x -> x.status().equals("PASS") || x.status().equals("FAIL")).count();
        long passed = signals.stream().filter(x -> x.status().equals("PASS")).count();
        long other = signals.size() - assessed;
        return passed + " of " + assessed + " checkable signals in place" + (other > 0 ? " (" + other + " not checked)" : "");
    }
}
