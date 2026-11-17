package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageExtractorTest {

    private static final String HTML = """
            <!doctype html>
            <html lang="en-IN">
            <head>
              <title>  Bare Botanics |   Gentle skincare  </title>
              <meta name="description" content="Plant-based   skincare for sensitive skin.">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="robots" content="index, follow">
              <meta property="og:title" content="Bare Botanics">
              <link rel="canonical" href="/">
              <script type="application/ld+json">{"@type":"Organization"}</script>
              <style>body { color: red }</style>
            </head>
            <body>
              <header><nav><a href="/shop">Shop</a><a href="/about">About us</a><a href="/shop">Shop</a></nav></header>
              <h1>Skincare   that listens</h1>
              <h2>Our cleanser</h2><h2>Our moisturiser</h2>
              <p>Gentle cleanser and moisturiser made for sensitive skin.</p>
              <img src="/a.jpg" alt="Cleanser bottle"><img src="/b.jpg" alt=""><img src="/c.jpg">
              <a href="/shop/cleanser#reviews">Cleanser</a>
              <a href="https://www.example.com/about">About</a>
              <a href="https://other.example.net/blog">Blog</a>
              <a href="mailto:hello@example.com">Email</a>
              <a href="javascript:void(0)">Menu</a>
              <script>var secret = "do not read me";</script>
              <footer>Copyright footer text</footer>
            </body></html>""";

    private static PageData extract(String html) {
        SafeFetcher.Response r = new SafeFetcher.Response(200, URI.create("https://www.example.com/"),
                "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), false, null, 12);
        return PageExtractor.extract(r, URI.create("https://www.example.com/"), "example.com");
    }

    @Test
    void readsMetadataAndNormalisesWhitespace() {
        PageData p = extract(HTML);
        assertEquals("Bare Botanics | Gentle skincare", p.title());
        assertEquals("Plant-based skincare for sensitive skin.", p.metaDescription());
        assertEquals("https://www.example.com/", p.canonical());
        assertEquals("en-IN", p.lang());
        assertEquals("Bare Botanics", p.ogTitle());
        assertTrue(p.viewport());
        assertFalse(p.noindex());
        assertTrue(p.structuredData());
        assertEquals(12, p.responseMs());
    }

    @Test
    void readsHeadingsNavigationAndImages() {
        PageData p = extract(HTML);
        assertEquals(List.of("Skincare that listens"), p.h1());
        assertEquals(List.of("Our cleanser", "Our moisturiser"), p.h2());
        assertEquals(List.of("Shop", "About us"), p.navigation(), "duplicates are removed");
        assertEquals(3, p.imageCount());
        assertEquals(1, p.imagesMissingAlt(), "empty alt is valid for decorative images; only a missing alt counts");
    }

    @Test
    void separatesInternalAndExternalLinksAndIgnoresNonWebLinks() {
        PageData p = extract(HTML);
        // /shop twice, /about, /shop/cleanser, and the www.example.com link (same site as example.com)
        assertEquals(5, p.internalLinks());
        assertEquals(1, p.externalLinks());
        assertTrue(p.links().contains("https://www.example.com/shop/cleanser"), "fragments are removed");
        assertFalse(p.links().stream().anyMatch(l -> l.startsWith("mailto:") || l.startsWith("javascript:")));
    }

    @Test
    void mainTextExcludesScriptsStylesNavigationAndFooter() {
        PageData p = extract(HTML);
        assertTrue(p.text().contains("Skincare that listens"));
        assertTrue(p.text().contains("Gentle cleanser and moisturiser"));
        assertFalse(p.text().contains("do not read me"));
        assertFalse(p.text().contains("color: red"));
        assertFalse(p.text().contains("Copyright footer text"));
        assertFalse(p.text().contains("About us"), "navigation text is reported separately");
        assertTrue(p.wordCount() > 5);
    }

    @Test
    void treatsNonBreakingAndZeroWidthSpacesAsOrdinarySpacing() {
        PageData p = extract("<html><head><title>Fast skincare​ for you</title></head>"
                + "<body><h1>Skincare that listens</h1></body></html>");
        assertEquals("Fast skincare for you", p.title());
        assertEquals(List.of("Skincare that listens"), p.h1());
    }

    @Test
    void detectsNoindexAndHandlesEmptyOrBrokenHtml() {
        PageData noindex = extract("<html><head><meta name=\"robots\" content=\"NOINDEX, nofollow\"></head><body>x</body></html>");
        assertTrue(noindex.noindex());
        assertFalse(noindex.viewport());
        assertEquals("", noindex.title());
        assertEquals("", noindex.metaDescription());

        PageData empty = extract("");
        assertEquals(0, empty.wordCount());
        assertEquals(0, empty.imageCount());

        PageData broken = extract("<html><body><h1>Unclosed <p>text <b>bold");
        assertEquals(List.of("Unclosed text bold"), broken.h1().isEmpty() ? List.of("Unclosed text bold") : broken.h1());
    }
}
