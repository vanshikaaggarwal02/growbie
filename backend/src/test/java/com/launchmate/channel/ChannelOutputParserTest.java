package com.launchmate.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.channel.ChannelOutputParser.Output;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ChannelOutputParserTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    private static final Set<String> TITLES = Set.of("Goa in 3 days", "Packing list for Manali", "Budget tips");
    private static final String EVIDENCE = """
            CADENCE (measured):
            - uploads in the sample: 3 over 14.0 days, about 1.0 per week
            - days since the latest upload: 2.0
            DESCRIPTIONS (measured):
            - empty descriptions: 2 of 3; average length 11.1 characters
            VIDEO 1: Goa in 3 days (https://www.youtube.com/watch?v=aaa)""";

    private static String theme(String name, String... titles) {
        StringBuilder t = new StringBuilder();
        for (String s : titles) {
            t.append(t.length() == 0 ? "" : ",").append('"').append(s).append('"');
        }
        return "{\"name\":\"" + name + "\",\"description\":\"A recurring topic.\",\"exampleTitles\":[" + t + "]}";
    }

    private static String strength(String evidence) {
        return "{\"point\":\"Steady uploads\",\"evidence\":\"" + evidence + "\"}";
    }

    private static String gap(String title, String evidence) {
        return "{\"title\":\"" + title + "\",\"reasoning\":\"Viewers cannot find links.\",\"evidence\":\"" + evidence
                + "\",\"suggestion\":\"Add a short description.\",\"basis\":\"OBSERVED\"}";
    }

    private static String idea(String title) {
        return "{\"title\":\"" + title + "\",\"hook\":\"Start with the price.\",\"format\":\"SHORT\",\"why\":\"Matches the budget theme.\"}";
    }

    private static String entry(int day, String format) {
        return "{\"day\":" + day + ",\"format\":\"" + format + "\",\"task\":\"Film it.\",\"titleIdea\":\"\"}";
    }

    private static String reply(String themes, String strengths, String gaps, String ideas, String calendar) {
        return "{\"summary\":\"A budget travel channel.\",\"themes\":[" + themes + "],\"strengths\":[" + strengths
                + "],\"gaps\":[" + gaps + "],\"differentiation\":[\"Student-focused routes.\"],\"ideas\":[" + ideas
                + "],\"calendar\":[" + calendar + "],\"limitations\":[\"Watch time was not available.\"]}";
    }

    private static String good() {
        return reply(theme("Trips", "Goa in 3 days", "Packing list for Manali"),
                strength("- days since the latest upload: 2.0"),
                gap("Empty descriptions", "- empty descriptions: 2 of 3; average length 11.1 characters"),
                String.join(",", idea("Kerala for under 5000"), idea("Delhi to Jaipur by bus"), idea("Hostel hacks")),
                entry(2, "SHORT") + "," + entry(5, "LONG_FORM"));
    }

    private Output parse(String text, boolean strict) {
        return ChannelOutputParser.parse(json, text, TITLES, EVIDENCE, strict);
    }

    @Test
    void acceptsAFullyVerifiableAnalysis() {
        Output out = parse(good(), true);
        assertEquals(1, out.themes().size());
        assertEquals(2, out.themes().get(0).exampleTitles().size());
        assertEquals(1, out.strengths().size());
        assertEquals(1, out.gaps().size());
        assertEquals(3, out.ideas().size());
        assertEquals(2, out.calendar().size());
        assertEquals(0, out.dropped());
    }

    @Test
    void strictModeRejectsAThemeTitleThatIsNotARealVideoAndSaysWhich() {
        String text = good().replace("Packing list for Manali\"]", "Made up video\"]");
        InvalidAiOutputException e = assertThrows(InvalidAiOutputException.class, () -> parse(text, true));
        assertTrue(e.getMessage().contains("Made up video"), e.getMessage());
    }

    @Test
    void lenientModeDropsUnverifiableItemsKeepsTheRestAndCounts() {
        String text = reply(
                theme("Trips", "Goa in 3 days", "Invented title") + "," + theme("Ghost theme", "Not a video"),
                strength("- days since the latest upload: 2.0") + "," + strength("- uploads per week: 7.0"),
                gap("Empty descriptions", "- empty descriptions: 2 of 3; average length 11.1 characters")
                        + "," + gap("Invented gap", "- retention drops at 40 seconds"),
                String.join(",", idea("Kerala for under 5000"), idea("Delhi to Jaipur by bus"), idea("Hostel hacks")),
                entry(1, "SHORT"));
        Output out = parse(text, false);
        assertEquals(1, out.themes().size(), "the theme with no real titles is dropped");
        assertEquals(java.util.List.of("Goa in 3 days"), out.themes().get(0).exampleTitles());
        assertEquals(1, out.strengths().size());
        assertEquals(1, out.gaps().size());
        assertEquals(3, out.dropped(), "one theme, one strength and one gap were removed");
    }

    @Test
    void toleratesCaseSpacingAndQuoteStylesInQuotesAndTitles() {
        String text = reply(theme("Trips", "  goa IN 3 days "),
                strength("\\\"- Days since the LATEST upload: 2.0\\\""),
                gap("Empty descriptions", "- empty descriptions:  2 of 3; average length 11.1 characters"),
                String.join(",", idea("Kerala for under 5000"), idea("Delhi to Jaipur by bus"), idea("Hostel hacks")),
                entry(1, "SHORT"));
        assertEquals(0, parse(text, true).dropped());
    }

    @Test
    void anInventedStatisticIsNotAcceptedAsEvidence() {
        String text = good().replace("average length 11.1 characters", "average length 9.9 characters");
        assertThrows(InvalidAiOutputException.class, () -> parse(text, true));
    }

    @Test
    void ideasMustBeNewAndAtLeastThreeMustSurvive() {
        String repeats = good().replace("Kerala for under 5000", "Goa in 3 days");
        assertThrows(InvalidAiOutputException.class, () -> parse(repeats, true), "repeating a title is refused");

        Output lenient = parse(repeats, false);
        assertEquals(2, lenient.ideas().size(), "the repeated title is dropped in lenient mode");

        String tooFew = reply(theme("Trips", "Goa in 3 days"), "", "", idea("Only one"), entry(1, "SHORT"));
        assertThrows(InvalidAiOutputException.class, () -> parse(tooFew, false), "fewer than three ideas is invalid");
    }

    @Test
    void theCalendarNeedsValidUniqueDaysAndFormats() {
        String duplicate = good().replace(entry(5, "LONG_FORM"), entry(2, "LONG_FORM"));
        assertThrows(InvalidAiOutputException.class, () -> parse(duplicate, false));
        String outOfRange = good().replace("\"day\":5", "\"day\":9");
        assertThrows(InvalidAiOutputException.class, () -> parse(outOfRange, false));
        String badFormat = good().replace("\"format\":\"LONG_FORM\",\"task\"", "\"format\":\"PODCAST\",\"task\"");
        assertThrows(InvalidAiOutputException.class, () -> parse(badFormat, false));

        Output sorted = parse(good().replace(entry(2, "SHORT") + "," + entry(5, "LONG_FORM"),
                entry(5, "LONG_FORM") + "," + entry(2, "SHORT")), true);
        assertEquals(2, sorted.calendar().get(0).day(), "entries are returned in day order");
    }

    @Test
    void ifNoThemeCanBeVerifiedTheAnalysisIsRefusedEvenInLenientMode() {
        String text = reply(theme("Ghost", "Not a video"), "", "",
                String.join(",", idea("A new idea one"), idea("A new idea two"), idea("A new idea three")),
                entry(1, "SHORT"));
        assertThrows(InvalidAiOutputException.class, () -> parse(text, false));
    }

    @Test
    void structuralProblemsAlwaysFail() {
        assertThrows(InvalidAiOutputException.class, () -> parse("not json", false));
        assertThrows(InvalidAiOutputException.class, () -> parse("{\"summary\":\"s\"}", false));
        assertThrows(InvalidAiOutputException.class, () -> parse(
                good().replace("\"basis\":\"OBSERVED\"", "\"basis\":\"CERTAIN\""), false));
        assertThrows(InvalidAiOutputException.class, () -> parse(
                good().replace("A budget travel channel.", "x".repeat(601)), false));
    }
}
