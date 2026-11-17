package com.launchmate.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.growth.PlanOutputParser.Output;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PlanOutputParserTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    private static final Set<String> IDS = Set.of("OWNER-1", "AUDIT-1", "AUDIT-2", "CHANNEL-G1", "DOMAIN-1", "LAUNCH-1");
    private static final String EVIDENCE = """
            - [OWNER-1] goal: Get first online orders
            - [AUDIT-1] HIGH SEO | No main heading | evidence: SINGLE_H1: FAIL - no h1 heading was found | fix: Add one h1.
            - [AUDIT-2] MEDIUM CONTENT | Page does not say what is offered | evidence: navigation: (none) | fix: Describe it.
            - [CHANNEL-G1] Empty descriptions | evidence: empty descriptions: 3 of 8 | suggestion: Add a short description.
            - [DOMAIN-1] barebotanics.in (USER_CANDIDATE): available when checked on 2026-11-14
            - [LAUNCH-1] website address: none given""";

    private static String rec(String title, String module, String ids, String evidence) {
        return "{\"title\":\"" + title + "\",\"module\":\"" + module + "\",\"impact\":\"HIGH\",\"effort\":\"LOW\","
                + "\"sourceIds\":[" + ids + "],\"evidence\":\"" + evidence + "\",\"rationale\":\"It helps visitors.\","
                + "\"suggestedAction\":\"Do it.\",\"howToMeasure\":\"Run the audit again.\",\"basis\":\"OBSERVED\"}";
    }

    private static String reply(String... recs) {
        return "{\"summary\":\"Fix the homepage first.\",\"recommendations\":[" + String.join(",", recs)
                + "],\"limitations\":[\"No sales data.\"]}";
    }

    private static String good() {
        return reply(
                rec("Add a main heading", "WEBSITE", "\"AUDIT-1\"", "SINGLE_H1: FAIL - no h1 heading was found"),
                rec("Describe what you sell", "WEBSITE", "\"AUDIT-2\"", "navigation: (none)"),
                rec("Fill in video descriptions", "CHANNEL", "\"CHANNEL-G1\"", "empty descriptions: 3 of 8"));
    }

    private Output parse(String text, boolean strict) {
        return PlanOutputParser.parse(json, text, IDS, EVIDENCE, strict);
    }

    @Test
    void acceptsAFullyVerifiablePlan() {
        Output out = parse(good(), true);
        assertEquals(3, out.recommendations().size());
        assertEquals(0, out.dropped());
        assertEquals("Fix the homepage first.", out.summary());
    }

    @Test
    void theModuleFollowsTheFirstEvidenceIdNotTheModelsClaim() {
        String text = reply(
                rec("Add a main heading", "BRAND", "\"AUDIT-1\"", "SINGLE_H1: FAIL - no h1 heading was found"),
                rec("Fix descriptions", "WEBSITE", "\"CHANNEL-G1\", \"AUDIT-1\"", "empty descriptions: 3 of 8"),
                rec("Secure the domain", "WEBSITE", "\"DOMAIN-1\"", "available when checked on 2026-11-14"));
        Output out = parse(text, true);
        assertEquals("WEBSITE", out.recommendations().get(0).module());
        assertEquals("CHANNEL", out.recommendations().get(1).module());
        assertEquals("DOMAIN", out.recommendations().get(2).module());
        assertEquals("LAUNCH", PlanOutputParser.moduleFor("LAUNCH-1", "WEBSITE"));
        assertEquals("BRAND", PlanOutputParser.moduleFor("OWNER-1", "BRAND"), "owner facts keep the claimed module");
    }

    @Test
    void toleratesSquareBracketsAndSpacingInIdsAndQuotes() {
        String text = reply(
                rec("Add a main heading", "WEBSITE", "\"[AUDIT-1]\"", "single_h1:   FAIL - no H1 heading was found"),
                rec("Describe what you sell", "WEBSITE", "\"AUDIT-2\"", "navigation: (none)"),
                rec("Fill in descriptions", "CHANNEL", "\"CHANNEL-G1\"", "empty descriptions: 3 of 8"));
        assertEquals(0, parse(text, true).dropped());
        assertEquals(java.util.List.of("AUDIT-1"), parse(text, true).recommendations().get(0).sourceIds());
    }

    @Test
    void strictModeRejectsAnUnknownIdOrInventedEvidenceAndSaysWhich() {
        String unknownId = good().replace("\"AUDIT-2\"", "\"AUDIT-99\"");
        InvalidAiOutputException e = assertThrows(InvalidAiOutputException.class, () -> parse(unknownId, true));
        assertTrue(e.getMessage().contains("AUDIT-99"), e.getMessage());

        String invented = good().replace("empty descriptions: 3 of 8", "empty descriptions: 8 of 8");
        InvalidAiOutputException e2 = assertThrows(InvalidAiOutputException.class, () -> parse(invented, true));
        assertTrue(e2.getMessage().contains("not copied exactly"), e2.getMessage());
    }

    @Test
    void lenientModeDropsUnverifiableOnesButNeedsThreeToRemain() {
        String four = reply(
                rec("Add a main heading", "WEBSITE", "\"AUDIT-1\"", "SINGLE_H1: FAIL - no h1 heading was found"),
                rec("Describe what you sell", "WEBSITE", "\"AUDIT-2\"", "navigation: (none)"),
                rec("Fill in descriptions", "CHANNEL", "\"CHANNEL-G1\"", "empty descriptions: 3 of 8"),
                rec("Invented", "WEBSITE", "\"AUDIT-1\"", "bounce rate is 80 percent"));
        Output out = parse(four, false);
        assertEquals(3, out.recommendations().size());
        assertEquals(1, out.dropped());

        String twoLeft = four.replace("empty descriptions: 3 of 8", "empty descriptions: 5 of 8");
        assertThrows(InvalidAiOutputException.class, () -> parse(twoLeft, false), "only two verifiable recommendations");
    }

    @Test
    void duplicateTitlesAreRemovedAndTheCountLimitsApply() {
        String dup = reply(
                rec("Add a main heading", "WEBSITE", "\"AUDIT-1\"", "SINGLE_H1: FAIL - no h1 heading was found"),
                rec("ADD A MAIN HEADING", "WEBSITE", "\"AUDIT-1\"", "SINGLE_H1: FAIL - no h1 heading was found"),
                rec("Describe what you sell", "WEBSITE", "\"AUDIT-2\"", "navigation: (none)"),
                rec("Fill in descriptions", "CHANNEL", "\"CHANNEL-G1\"", "empty descriptions: 3 of 8"));
        assertEquals(3, parse(dup, true).recommendations().size());

        String two = reply(
                rec("Add a main heading", "WEBSITE", "\"AUDIT-1\"", "SINGLE_H1: FAIL - no h1 heading was found"),
                rec("Describe what you sell", "WEBSITE", "\"AUDIT-2\"", "navigation: (none)"));
        assertThrows(InvalidAiOutputException.class, () -> parse(two, false), "fewer than 3 is invalid");

        String[] eleven = new String[11];
        for (int i = 0; i < 11; i++) {
            eleven[i] = rec("Item " + i, "WEBSITE", "\"AUDIT-1\"", "navigation: (none)");
        }
        assertThrows(InvalidAiOutputException.class, () -> parse(reply(eleven), false), "more than 10 is invalid");
    }

    @Test
    void structuralProblemsAlwaysFail() {
        assertThrows(InvalidAiOutputException.class, () -> parse("not json", false));
        assertThrows(InvalidAiOutputException.class, () -> parse("{\"summary\":\"s\"}", false));
        assertThrows(InvalidAiOutputException.class, () -> parse(good().replace("\"impact\":\"HIGH\"", "\"impact\":\"HUGE\""), false));
        assertThrows(InvalidAiOutputException.class, () -> parse(good().replace("\"basis\":\"OBSERVED\"", "\"basis\":\"CERTAIN\""), false));
        assertThrows(InvalidAiOutputException.class, () -> parse(good().replace("\"AUDIT-1\"", "\"\""), false), "an empty id is invalid");
    }
}
