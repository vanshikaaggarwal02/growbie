package com.launchmate.website;

import java.util.List;

/**
 * Everything the audit knows about one fetched page. All of it is measured from the page's HTML.
 * {@code links} (same-site addresses to crawl next) and {@code text} are working data and are not
 * stored with the audit.
 */
public record PageData(
        String url,
        int status,
        String title,
        String metaDescription,
        String canonical,
        String lang,
        boolean viewport,
        boolean noindex,
        List<String> h1,
        List<String> h2,
        List<String> navigation,
        int imageCount,
        int imagesMissingAlt,
        int internalLinks,
        int externalLinks,
        int wordCount,
        String text,
        String ogTitle,
        boolean structuredData,
        long responseMs,
        int bytes,
        boolean truncated,
        List<String> links) {}
