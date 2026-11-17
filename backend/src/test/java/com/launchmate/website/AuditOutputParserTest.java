package com.launchmate.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.website.AuditOutputParser.Output;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AuditOutputParserTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    private static final Set<String> PAGES = Set.of("https://example.com/", "https://example.com/about");
    private static final String EVIDENCE = """
            SITE: https://example.com/ (host: example.com)
            - META_DESCRIPTION_PRESENT: FAIL - no meta description tag was found
            - performance score: 62
            - largest contentful paint: 4.1 s
            PAGE 1: https://example.com/
              title: Home
              h1: Welcome to “Bare Botanics” — skincare
              text: We make gentle skincare for sensitive skin.
            PAGE 2: https://example.com/about
              title: About us""";

    private static String finding(String title, String url, String evidence) {
        return "{\"category\":\"SEO\",\"severity\":\"HIGH\",\"pageUrl\":\"" + url + "\",\"title\":\"" + title
                + "\",\"evidence\":\"" + evidence.replace("\"", "\\\"") + "\",\"impact\":\"Visitors cannot tell what is sold.\","
                + "\"recommendation\":\"Rewrite it.\",\"suggestedCopy\":\"\",\"basis\":\"OBSERVED\"}";
    }

    private static String reply(String... findings) {
        return "{\"summary\":\"A small site.\",\"findings\":[" + String.join(",", findings)
                + "],\"limitations\":[\"Only two pages were audited.\"]}";
    }

    private Output parse(String text, boolean strict) {
        return AuditOutputParser.parse(json, text, PAGES, EVIDENCE, strict);
    }

    @Test
    void keepsFindingsWhoseEvidenceIsQuotedFromTheEvidence() {
        Output out = parse(reply(
                finding("Title is a single generic word", "https://example.com/", "title: Home"),
                finding("No meta description", "https://example.com/",
                        "META_DESCRIPTION_PRESENT: FAIL - no meta description tag was found"),
                finding("Largest content is slow to appear", "https://example.com/", "largest contentful paint: 4.1 s")),
                true);
        assertEquals(3, out.findings().size());
        assertEquals(0, out.dropped());
        assertEquals("Only two pages were audited.", out.limitations().get(0));
    }

    @Test
    void toleratesCaseSpacingQuotesAndTypographicPunctuationButNotDifferentWords() {
        Output out = parse(reply(
                // straight quotes and a plain hyphen where the page has curly quotes and an em dash
                finding("Heading style", "https://example.com/", "H1:   welcome to \"bare botanics\" - SKINCARE"),
                finding("Quoted whole", "https://example.com/", "\"title: Home\"")), true);
        assertEquals(2, out.findings().size());

        assertThrows(InvalidAiOutputException.class, () -> parse(reply(
                finding("Reworded", "https://example.com/", "title: Homepage")), true));
    }

    @Test
    void strictModeRejectsInventedEvidenceAndExplainsWhy() {
        InvalidAiOutputException e = assertThrows(InvalidAiOutputException.class, () -> parse(reply(
                finding("Real", "https://example.com/", "title: Home"),
                finding("Invented speed claim", "https://example.com/", "largest contentful paint: 9.7 s")), true));
        assertTrue(e.getMessage().contains("Invented speed claim"), e.getMessage());
        assertTrue(e.getMessage().contains("not copied exactly"), e.getMessage());
    }

    @Test
    void lenientModeDropsUnverifiableFindingsKeepsTheRestAndCountsThem() {
        Output out = parse(reply(
                finding("Real", "https://example.com/", "title: Home"),
                finding("Invented number", "https://example.com/", "performance score: 31"),
                finding("Wrong page", "https://example.com/missing", "title: Home"),
                finding("Also real", "https://example.com/about", "title: About us")), false);
        assertEquals(2, out.findings().size());
        assertEquals(2, out.dropped());
        assertEquals("Real", out.findings().get(0).title());
        assertEquals("Also real", out.findings().get(1).title());
    }

    @Test
    void rejectsAPageThatWasNotAudited() {
        InvalidAiOutputException e = assertThrows(InvalidAiOutputException.class, () -> parse(reply(
                finding("Elsewhere", "https://other.example.net/", "title: Home")), true));
        assertTrue(e.getMessage().contains("not one of the audited pages"), e.getMessage());
    }

    @Test
    void rejectsEvidenceTooShortToVerify() {
        assertThrows(InvalidAiOutputException.class, () -> parse(reply(
                finding("Tiny", "https://example.com/", "a")), false));
    }

    @Test
    void structuralProblemsFailInBothModes() {
        String badSeverity = reply(finding("X", "https://example.com/", "title: Home")).replace("HIGH", "CRITICAL");
        assertThrows(InvalidAiOutputException.class, () -> parse(badSeverity, false));
        assertThrows(InvalidAiOutputException.class, () -> parse("not json", false));
        assertThrows(InvalidAiOutputException.class, () -> parse("{\"summary\":\"s\"}", false));
        assertThrows(InvalidAiOutputException.class, () -> parse(
                reply(finding("T", "https://example.com/", "title: Home")).replace("A small site.", "x".repeat(601)),
                false));
    }

    @Test
    void rejectsMoreThanTwelveFindingsAndRemovesDuplicateTitles() {
        String[] thirteen = new String[13];
        for (int i = 0; i < 13; i++) {
            thirteen[i] = finding("Finding " + i, "https://example.com/", "title: Home");
        }
        assertThrows(InvalidAiOutputException.class, () -> parse(reply(thirteen), false));

        Output out = parse(reply(
                finding("Same title", "https://example.com/", "title: Home"),
                finding("same TITLE", "https://example.com/", "title: Home")), true);
        assertEquals(1, out.findings().size());
    }

    @Test
    void anAuditWithNoFindingsIsValid() {
        Output out = parse(reply(), true);
        assertEquals(0, out.findings().size());
        assertEquals("A small site.", out.summary());
    }
}
