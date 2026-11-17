package com.launchmate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.domain.FitOutputParser.FitOutput;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FitOutputParserTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private static final Set<String> DOMAINS = Set.of("barebotanics.com", "xyz123.shop");

    private static String assessment(String domain, String verdict) {
        return "{\"domain\":\"" + domain + "\",\"verdict\":\"" + verdict + "\","
                + "\"strengths\":[\"12 letters, no hyphens or digits\"],\"concerns\":[],"
                + "\"suggestion\":\"Keep it and confirm availability again before buying.\"}";
    }

    private static final String REVIEW = """
            {"domain":"barebotanics.com","recommendation":"KEEP_CURRENT","reasoning":"It matches the brand exactly.",
             "risks":["Search ranking could drop during a move","Inbound links could break",
                      "Email addresses on the domain would stop working","Customers know this address"],
             "checklist":["Keep the old domain registered","Set up permanent redirects",
                          "Move and test email","Update the address in search tools"]}""";

    private static String reply(String assessments, String reviews) {
        return "{\"assessments\":[" + assessments + "],\"currentDomainReviews\":[" + reviews
                + "],\"limitations\":[\"Trademark and existing-brand conflicts were not checked.\"]}";
    }

    @Test
    void acceptsCompleteOutputWithACurrentDomainReview() {
        String text = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("xyz123.shop", "WEAK"), REVIEW);
        FitOutput out = FitOutputParser.parse(json, text, DOMAINS, "barebotanics.com");
        assertEquals("STRONG", out.byDomain().get("barebotanics.com").verdict());
        assertEquals("WEAK", out.byDomain().get("xyz123.shop").verdict());
        assertEquals("KEEP_CURRENT", out.currentReview().recommendation());
        assertEquals(4, out.currentReview().risks().size());
    }

    @Test
    void acceptsOutputWithNoCurrentDomain() {
        String text = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("xyz123.shop", "ACCEPTABLE"), "");
        FitOutput out = FitOutputParser.parse(json, text, DOMAINS, null);
        assertNull(out.currentReview());
        assertNotNull(out.byDomain().get("xyz123.shop"));
    }

    @Test
    void rejectsADomainThatWasNotGiven() {
        String text = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("other.com", "WEAK"), "");
        assertThrows(InvalidAiOutputException.class, () -> FitOutputParser.parse(json, text, DOMAINS, null));
    }

    @Test
    void rejectsAMissingOrDuplicatedAssessment() {
        String missing = reply(assessment("barebotanics.com", "STRONG"), "");
        assertThrows(InvalidAiOutputException.class, () -> FitOutputParser.parse(json, missing, DOMAINS, null));

        String twice = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("barebotanics.com", "WEAK"), "");
        assertThrows(InvalidAiOutputException.class, () -> FitOutputParser.parse(json, twice, DOMAINS, null));
    }

    @Test
    void rejectsAnInvalidVerdict() {
        String text = reply(assessment("barebotanics.com", "EXCELLENT") + "," + assessment("xyz123.shop", "WEAK"), "");
        assertThrows(InvalidAiOutputException.class, () -> FitOutputParser.parse(json, text, DOMAINS, null));
    }

    @Test
    void rejectsAReviewWhenNoDomainIsCurrent() {
        String text = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("xyz123.shop", "WEAK"), REVIEW);
        assertThrows(InvalidAiOutputException.class, () -> FitOutputParser.parse(json, text, DOMAINS, null));
    }

    @Test
    void requiresAReviewWhenADomainIsCurrent() {
        String text = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("xyz123.shop", "WEAK"), "");
        assertThrows(InvalidAiOutputException.class,
                () -> FitOutputParser.parse(json, text, DOMAINS, "barebotanics.com"));
    }

    @Test
    void rejectsAReviewForTheWrongDomainOrWithTooFewRisks() {
        String wrongDomain = REVIEW.replace("\"domain\":\"barebotanics.com\"", "\"domain\":\"xyz123.shop\"");
        String a = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("xyz123.shop", "WEAK"), wrongDomain);
        assertThrows(InvalidAiOutputException.class,
                () -> FitOutputParser.parse(json, a, DOMAINS, "barebotanics.com"));

        String fewRisks = REVIEW.replaceAll("(?s)\"risks\":\\[.*?\\]", "\"risks\":[\"Only one risk\"]");
        String b = reply(assessment("barebotanics.com", "STRONG") + "," + assessment("xyz123.shop", "WEAK"), fewRisks);
        assertThrows(InvalidAiOutputException.class,
                () -> FitOutputParser.parse(json, b, DOMAINS, "barebotanics.com"));
    }
}
