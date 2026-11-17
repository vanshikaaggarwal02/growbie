package com.launchmate.brand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.brand.BrandOutputParser.BrandOutput;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class BrandOutputParserTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    private static final String VALID = """
            {"names":[
              {"name":"Leafmark","tagline":"Gentle skincare for sensitive skin","rationale":"Short and easy to spell.",
               "domainCandidates":["https://www.Leafmark.com/","bad_domain"]},
              {"name":"leafmark","tagline":"Duplicate of the first","rationale":"Same name again.","domainCandidates":[]},
              {"name":"Soothe & Co","tagline":"Calm skin, simple routine","rationale":"Speaks to sensitivity.",
               "domainCandidates":["soothe.com"]}
            ],
            "positioning":["For adults with sensitive skin who want fewer, gentler products."],
            "limitations":["Trademark and existing-brand conflicts were not checked."]}""";

    @Test
    void acceptsValidOutputNormalisesDomainsAndDropsDuplicatesAndBadCandidates() {
        BrandOutput out = BrandOutputParser.parse(json, VALID, 6);
        assertEquals(2, out.names().size(), "the repeated name must be dropped");
        assertEquals(List.of("leafmark.com"), out.names().get(0).domainCandidates(),
                "the URL is normalised and the invalid candidate is dropped");
        assertEquals(List.of("soothe.com"), out.names().get(1).domainCandidates());
        assertEquals(1, out.positioning().size());
    }

    @Test
    void rejectsMoreNamesThanRequested() {
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, VALID, 2));
    }

    @Test
    void rejectsMalformedAndNonObjectReplies() {
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, "not json", 6));
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, "[1,2]", 6));
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, "", 6));
    }

    @Test
    void rejectsMissingFieldsAndOverlongText() {
        String missingTagline = """
                {"names":[{"name":"A","rationale":"r","domainCandidates":[]}],"positioning":[],"limitations":[]}""";
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, missingTagline, 6));

        String longTagline = "{\"names\":[{\"name\":\"A\",\"tagline\":\"" + "x".repeat(121)
                + "\",\"rationale\":\"r\",\"domainCandidates\":[]}],\"positioning\":[],\"limitations\":[]}";
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, longTagline, 6));
    }

    @Test
    void rejectsMoreThanTwoDomainCandidatesPerName() {
        String three = """
                {"names":[{"name":"A","tagline":"t","rationale":"r",
                 "domainCandidates":["a.com","a.in","a.net"]}],"positioning":[],"limitations":[]}""";
        assertThrows(InvalidAiOutputException.class, () -> BrandOutputParser.parse(json, three, 6));
    }
}
