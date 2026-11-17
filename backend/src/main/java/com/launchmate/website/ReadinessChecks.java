package com.launchmate.website;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The deterministic part of the audit: plain pass or fail checks measured from the homepage and the
 * PageSpeed result. {@code passed} is null when a check cannot be made (for example PageSpeed was
 * unavailable); such a check is never counted as a pass or a fail. This is a checklist of presence
 * basics, not a measure of business success.
 */
public final class ReadinessChecks {

    public record Check(String key, String label, Boolean passed, String detail, String source) {}

    private static final Set<String> GENERIC_TITLES = Set.of(
            "home", "homepage", "index", "untitled", "untitled document", "welcome", "new page", "default");
    private static final int GOOD_SCORE = 90;
    private static final String SRC = "OBSERVED_PUBLIC_DATA";

    private ReadinessChecks() {}

    public static List<Check> evaluate(PageData home, URI finalUri, PageSpeedClient.Result psi) {
        List<Check> c = new ArrayList<>();

        String title = home.title();
        c.add(new Check("TITLE_PRESENT", "Homepage has a title", !title.isEmpty(),
                title.isEmpty() ? "no title tag was found" : "the title is \"" + title + "\"", SRC));
        if (title.isEmpty()) {
            c.add(unavailable("TITLE_SPECIFIC", "Title is more than a generic word", "there is no title to assess"));
            c.add(unavailable("TITLE_LENGTH", "Title is 30 to 60 characters", "there is no title to measure"));
        } else {
            boolean generic = GENERIC_TITLES.contains(title.toLowerCase(Locale.ROOT));
            c.add(new Check("TITLE_SPECIFIC", "Title is more than a generic word", !generic,
                    generic ? "\"" + title + "\" is a generic word" : "the title is not a generic word", SRC));
            int n = title.length();
            c.add(new Check("TITLE_LENGTH", "Title is 30 to 60 characters", n >= 30 && n <= 60,
                    "the title is " + n + " characters (30 to 60 is common guidance, not a rule)", SRC));
        }

        String desc = home.metaDescription();
        c.add(new Check("META_DESCRIPTION_PRESENT", "Homepage has a meta description", !desc.isEmpty(),
                desc.isEmpty() ? "no meta description tag was found" : "the meta description is \"" + desc + "\"", SRC));
        if (desc.isEmpty()) {
            c.add(unavailable("META_DESCRIPTION_LENGTH", "Meta description is 70 to 160 characters",
                    "there is no meta description to measure"));
        } else {
            int n = desc.length();
            c.add(new Check("META_DESCRIPTION_LENGTH", "Meta description is 70 to 160 characters",
                    n >= 70 && n <= 160,
                    "the meta description is " + n + " characters (70 to 160 is common guidance, not a rule)", SRC));
        }

        int h1 = home.h1().size();
        c.add(new Check("SINGLE_H1", "Homepage has exactly one main heading (h1)", h1 == 1,
                h1 == 0 ? "no h1 heading was found" : h1 == 1 ? "the h1 is \"" + home.h1().get(0) + "\""
                        : h1 + " h1 headings were found", SRC));

        c.add(new Check("VIEWPORT", "Page declares a mobile viewport", home.viewport(),
                home.viewport() ? "a viewport meta tag is present" : "no viewport meta tag was found", SRC));
        c.add(new Check("LANGUAGE", "Page declares its language", !home.lang().isEmpty(),
                home.lang().isEmpty() ? "no lang attribute on the html tag" : "the language is \"" + home.lang() + "\"",
                SRC));

        boolean altOk = home.imagesMissingAlt() == 0;
        c.add(new Check("IMAGES_ALT", "Images have alt text", altOk,
                home.imageCount() == 0 ? "the homepage has no images"
                        : home.imagesMissingAlt() + " of " + home.imageCount() + " images have no alt attribute", SRC));

        boolean https = "https".equalsIgnoreCase(finalUri.getScheme());
        c.add(new Check("HTTPS", "Site is served over HTTPS", https,
                https ? "the homepage is served over HTTPS" : "the homepage is served over plain HTTP", SRC));
        c.add(new Check("INDEXABLE", "Homepage is not blocked from search engines", !home.noindex(),
                home.noindex() ? "the page has a noindex robots tag" : "no noindex robots tag was found", SRC));

        c.add(speed(psi, "performance", "PAGESPEED_PERFORMANCE", "PageSpeed performance is good"));
        c.add(speed(psi, "accessibility", "PAGESPEED_ACCESSIBILITY", "PageSpeed accessibility is good"));
        c.add(speed(psi, "bestPractices", "PAGESPEED_BEST_PRACTICES", "PageSpeed best practices are good"));
        c.add(speed(psi, "seo", "PAGESPEED_SEO", "PageSpeed SEO is good"));
        return List.copyOf(c);
    }

    public static String summary(List<Check> checks) {
        long assessed = checks.stream().filter(x -> x.passed() != null).count();
        long passed = checks.stream().filter(x -> Boolean.TRUE.equals(x.passed())).count();
        long unavailable = checks.size() - assessed;
        return passed + " of " + assessed + " checks passed"
                + (unavailable > 0 ? " (" + unavailable + " could not be assessed)" : "");
    }

    private static Check speed(PageSpeedClient.Result psi, String category, String key, String label) {
        Integer score = psi.available() ? psi.scores().get(category) : null;
        if (score == null) {
            return unavailable(key, label, "PageSpeed data was not available");
        }
        return new Check(key, label, score >= GOOD_SCORE,
                "the mobile " + category + " score is " + score + " (" + GOOD_SCORE + " or more counts as good)", SRC);
    }

    private static Check unavailable(String key, String label, String reason) {
        return new Check(key, label, null, reason, SRC);
    }
}
