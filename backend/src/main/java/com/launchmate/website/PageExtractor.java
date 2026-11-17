package com.launchmate.website;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Reads the facts the audit needs out of a page's HTML. */
public final class PageExtractor {

    private static final int MAX_TEXT_CHARS = 20_000;
    private static final int MAX_LINKS = 60;
    private static final int MAX_HEADINGS = 8;
    private static final int MAX_NAV = 15;

    private PageExtractor() {}

    /** {@code siteKey} is the site's host without a leading "www.", used to tell internal links apart. */
    public static PageData extract(SafeFetcher.Response response, URI finalUri, String siteKey) {
        Document doc;
        try {
            doc = Jsoup.parse(new ByteArrayInputStream(response.body()), null, finalUri.toString());
        } catch (IOException e) {
            doc = Jsoup.parse("");
        }

        String title = collapse(doc.title());
        String metaDescription = collapse(attr(doc, "meta[name=description]", "content"));
        Element canonicalLink = doc.selectFirst("link[rel=canonical]");
        String canonical = canonicalLink == null ? "" : canonicalLink.absUrl("href");
        Element html = doc.selectFirst("html");
        String lang = html == null ? "" : html.attr("lang").trim();
        boolean viewport = doc.selectFirst("meta[name=viewport]") != null;
        String robotsMeta = (attr(doc, "meta[name=robots]", "content") + " "
                + attr(doc, "meta[name=googlebot]", "content")).toLowerCase(Locale.ROOT);
        boolean noindex = robotsMeta.contains("noindex");

        List<String> h1 = headings(doc, "h1", 5);
        List<String> h2 = headings(doc, "h2", MAX_HEADINGS);

        Set<String> nav = new LinkedHashSet<>();
        for (Element a : doc.select("nav a, header a")) {
            String text = collapse(a.text());
            if (!text.isEmpty() && text.length() <= 40 && nav.size() < MAX_NAV) {
                nav.add(text);
            }
        }

        int imageCount = doc.select("img").size();
        int imagesMissingAlt = doc.select("img:not([alt])").size();

        Set<String> internal = new LinkedHashSet<>();
        int internalLinks = 0;
        int externalLinks = 0;
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                continue;
            }
            int hash = href.indexOf('#');
            if (hash >= 0) {
                href = href.substring(0, hash);
            }
            if (isSameSite(href, siteKey)) {
                internalLinks++;
                if (!href.isEmpty() && internal.size() < MAX_LINKS) {
                    internal.add(href);
                }
            } else {
                externalLinks++;
            }
        }

        // Main text: navigation, footers and scripts are removed so repeated chrome is not counted.
        Document textDoc = doc.clone();
        textDoc.select("script, style, noscript, svg, nav, footer, form").remove();
        String text = collapse(textDoc.body() == null ? "" : textDoc.body().text());
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        int wordCount = text.isEmpty() ? 0 : text.split("\\s+").length;

        return new PageData(finalUri.toString(), response.status(), title, metaDescription, canonical, lang,
                viewport, noindex, h1, h2, List.copyOf(nav), imageCount, imagesMissingAlt, internalLinks,
                externalLinks, wordCount, text, collapse(attr(doc, "meta[property=og:title]", "content")),
                !doc.select("script[type=application/ld+json]").isEmpty(), response.elapsedMs(),
                response.body().length, response.truncated(), List.copyOf(internal));
    }

    static boolean isSameSite(String url, String siteKey) {
        try {
            String host = URI.create(url).getHost();
            return host != null && siteKey(host).equals(siteKey);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String siteKey(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private static List<String> headings(Document doc, String tag, int max) {
        List<String> out = new ArrayList<>();
        for (Element h : doc.select(tag)) {
            String text = collapse(h.text());
            if (!text.isEmpty() && out.size() < max) {
                out.add(text.length() > 140 ? text.substring(0, 140) : text);
            }
        }
        return List.copyOf(out);
    }

    private static String attr(Document doc, String selector, String attribute) {
        Element e = doc.selectFirst(selector);
        return e == null ? "" : e.attr(attribute);
    }

    /**
     * Collapses every kind of whitespace to one plain space, including the non-breaking spaces that
     * PageSpeed and many web pages use (for example in "0.8 s"), and removes zero-width spaces.
     */
    static String collapse(String s) {
        return com.launchmate.ai.Quotes.collapse(s);
    }
}
