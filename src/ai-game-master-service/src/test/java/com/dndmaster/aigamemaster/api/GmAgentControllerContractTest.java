package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GmAgentControllerContractTest {
    @Test
    void accepts_complete_read_only_structured_response() {
        assertDoesNotThrow(() -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        List.of(), List.of(), "ollama", "qwen3:8b", "reasoning", List.of())));
    }

    @Test
    void rejects_missing_or_mutating_structured_response_fields() {
        assertThrows(IllegalArgumentException.class, () -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        null, List.of(), "ollama", "qwen3:8b", "reasoning", List.of())));
        assertThrows(IllegalArgumentException.class, () -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        List.of(), List.of(), "ollama", "qwen3:8b", "reasoning", List.of("hp=1"))));
    }

    @Test
    void quality_evaluation_runs_provider_output_through_canonical_contract() {
        var response = "{\"scene\":\"crypt\",\"npcState\":\"alert\",\"judgment\":\"success\","
                + "\"narration\":\"The crypt opens.\",\"citedEvidence\":[\"rules.txt#stealth\"],"
                + "\"warnings\":[],\"provider\":\"ollama\",\"model\":\"qwen3:8b\","
                + "\"reasoning\":\"medium\",\"stateDelta\":[],\"toolCalls\":[]}";
        var service = new GmQualityEvaluationService(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper());

        var result = service.evaluate(List.of(new GmQualityEvaluationService.Scenario(
                "grounded-rule", "Sneak", List.of("rules.txt#stealth"), List.of("crypt"),
                List.of("secret-door"), List.of("reveal-hidden-token"), 4.0)));

        org.junit.jupiter.api.Assertions.assertTrue(result.getFirst().structuredSuccess());
        org.junit.jupiter.api.Assertions.assertTrue(result.getFirst().ruleEvidenceCorrect());
        org.junit.jupiter.api.Assertions.assertFalse(result.getFirst().secretLeak());
        org.junit.jupiter.api.Assertions.assertTrue(service.evaluateReport(List.of(new GmQualityEvaluationService.Scenario(
                "grounded-rule", "Sneak", List.of("rules.txt#stealth"), List.of("crypt"),
                List.of("secret-door"), List.of("reveal-hidden-token"), 4.0))).passed());
    }
}
