package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ResolutionCandidateControllerTest {
    @Test
    void acceptsJsonWrappedInModelMarkdownAndKeepsValidCandidates() {
        var controller = new ResolutionCandidateController(null, new ObjectMapper());

        var candidates = controller.parseModel("Here is the result:\n```json\n"
                + "[{\"kind\":\"SKILL_ABILITY_CHECK\",\"abilityOrSkill\":\"Perception\","
                + "\"visibility\":\"GM_REFERENCE\",\"sourceQuote\":\"check the door\","
                + "\"sourceRefs\":[],\"provenance\":\"schema-v1\"},"
                + "{\"kind\":\"NOT_SUPPORTED\"}]\n```\n");

        assertEquals(1, candidates.size());
        assertEquals("SKILL_ABILITY_CHECK", candidates.getFirst().kind());
    }
}
