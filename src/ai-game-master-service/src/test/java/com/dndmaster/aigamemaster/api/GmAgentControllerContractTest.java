package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GmAgentControllerContractTest {
    @Test
    void provider_failure_is_not_committed_as_a_successful_fallback() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                                   com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new RuntimeException("provider unavailable");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var exception = assertThrows(ResponseStatusException.class,
                () -> controller.plan("token", request()));

        org.junit.jupiter.api.Assertions.assertEquals(503, exception.getStatusCode().value());
    }

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
        assertThrows(IllegalArgumentException.class, () -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "", "narration", null,
                        List.of(), List.of(), "ollama", "qwen3:8b", "reasoning", List.of())));
        assertThrows(IllegalArgumentException.class, () -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        List.of(), List.of(), "", "qwen3:8b", "reasoning", List.of())));
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

    @Test
    void quality_evaluation_fails_closed_on_provider_scalar_lists() {
        var response = "{\"scene\":\"crypt\",\"npcState\":\"alert\",\"judgment\":\"success\","
                + "\"narration\":\"The crypt opens.\",\"citedEvidence\":\"rules.txt#stealth\","
                + "\"warnings\":\"none\",\"provider\":\"ollama\",\"model\":\"qwen3:8b\","
                + "\"reasoning\":\"medium\",\"stateDelta\":[],\"toolCalls\":[]}";
        var service = new GmQualityEvaluationService(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper());

        var result = service.evaluate(List.of(new GmQualityEvaluationService.Scenario(
                "scalar-lists", "Sneak", List.of("rules.txt#stealth"), List.of("crypt"),
                List.of("secret-door"), List.of("reveal-hidden-token"), 4.0)));

        org.junit.jupiter.api.Assertions.assertFalse(result.getFirst().structuredSuccess());
        org.junit.jupiter.api.Assertions.assertEquals("ProviderMalformedResponseException", result.getFirst().failure());
    }

    private static GmAgentController.Request request() {
        return new GmAgentController.Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding",
                "", "", List.of(), List.of(), List.of(), List.of(), List.of(), "", "", "", "");
    }
}
