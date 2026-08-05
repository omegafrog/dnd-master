package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

    @Test
    void normalizesOllamaJsonWrapperAndCommonEnumAliasesInsteadOfDroppingCandidate() {
        var controller = new ResolutionCandidateController(null, new ObjectMapper());

        var candidates = controller.parseModel("{\"config\":{},\"response\":\"["
                + "{\\\"kind\\\":\\\"saving throw\\\",\\\"abilityOrSkill\\\":\\\"Dexterity\\\","
                + "\\\"dc\\\":12,\\\"diceExpression\\\":\\\"1d10\\\",\\\"visibility\\\":\\\"public\\\","
                + "\\\"sourceQuote\\\":\\\"DC 12 Dexterity saving throw\\\",\\\"sourceRefs\\\":[\\\"unstructured source\\\"],"
                + "\\\"detail\\\":\\\"half damage on success\\\",\\\"provenance\\\":\\\"source text\\\"}"
                + "]\"}");

        assertEquals(1, candidates.size());
        assertEquals("SAVING_THROW", candidates.getFirst().kind());
        assertEquals("GM_REFERENCE", candidates.getFirst().visibility());
        assertEquals(12, candidates.getFirst().dc());
    }

    @Test
    void extractsGroundedFallbackWhenModelReturnsEmptyArray() {
        var candidates = ResolutionCandidateController.fallbackCandidates(List.of(
                new ResolutionCandidateController.Excerpt(
                        java.util.UUID.randomUUID(), 2, "offset 10-100",
                        "The mosaic requires a DC 12 Dexterity saving throw, taking 5 (1d10) damage.")));

        assertFalse(candidates.isEmpty());
        assertEquals("SAVING_THROW", candidates.getFirst().kind());
        assertEquals(12, candidates.getFirst().dc());
    }

    @Test
    void rejectsTruncatedPdfTokenInsteadOfCreatingFalseCandidate() {
        var candidates = ResolutionCandidateController.fallbackCandidates(List.of(
                new ResolutionCandidateController.Excerpt(
                        java.util.UUID.randomUUID(), 2, "offset 10-100",
                        "The creature must make a DC 12 Dexterity sa")));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void acceptsPdfLineBreakInsideSavingThrowWord() {
        var candidates = ResolutionCandidateController.fallbackCandidates(List.of(
                new ResolutionCandidateController.Excerpt(
                        java.util.UUID.randomUUID(), 4, "offset 4858-9445",
                        "Any creature must make a DC 12 Dexterity sa\nving throw, taking 5 damage.")));

        assertEquals(1, candidates.size());
        assertEquals("SAVING_THROW", candidates.getFirst().kind());
        assertEquals(12, candidates.getFirst().dc());
    }
}
