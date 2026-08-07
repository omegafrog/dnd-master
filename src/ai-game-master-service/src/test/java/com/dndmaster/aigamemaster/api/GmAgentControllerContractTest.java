package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GmAgentControllerContractTest {
    @Test
    void tagged_gm_only_narration_is_not_part_of_public_narration() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                                   com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"brewery\",\"npcState\":\"calm\",\"judgment\":\"observing\","
                        + "\"narration\":\"fallback\",\"narrationSegments\":["
                        + "{\"visibility\":\"PLAYER_VISIBLE\",\"text\":\"The door is closed.\"},"
                        + "{\"visibility\":\"GM_ONLY\",\"text\":\"The hidden key is under the vat.\"}],"
                        + "\"proposedActiveSourceContext\":null,\"citedEvidence\":[],\"warnings\":[],"
                        + "\"provider\":\"ollama\",\"model\":\"qwen3:8b\",\"reasoning\":\"grounded\","
                        + "\"stateDelta\":[],\"toolCalls\":[]}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var response = controller.plan("token", request());

        org.junit.jupiter.api.Assertions.assertEquals("The door is closed.", response.narration());
        org.junit.jupiter.api.Assertions.assertTrue(response.narrationSegments().stream().anyMatch(s -> "GM_ONLY".equals(s.visibility())));
    }

    @Test
    void structured_prompt_forces_safe_empty_citations_when_exact_evidence_is_unavailable() {
        String[] captured = {null};
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                                   com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                captured[0] = prompt;
                return parser.parse("{\"scene\":\"brewery\",\"npcState\":\"calm\",\"judgment\":\"observing\","
                        + "\"narration\":\"You inspect the room.\",\"proposedActiveSourceContext\":null,"
                        + "\"citedEvidence\":[],\"warnings\":[],\"provider\":\"ollama\","
                        + "\"model\":\"qwen3:8b\",\"reasoning\":\"grounded\",\"stateDelta\":[],\"toolCalls\":[]}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        controller.plan("token", request());

        org.junit.jupiter.api.Assertions.assertTrue(captured[0].contains("citedEvidence is an array of exact evidence objects"));
        org.junit.jupiter.api.Assertions.assertTrue(captured[0].contains("Do not cite or reproduce hidden DCs"));
    }

    @Test
    void unverified_provider_citation_fails_closed() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                                   com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"brewery\",\"npcState\":\"calm\",\"judgment\":\"observing\","
                        + "\"narration\":\"You inspect the room.\",\"proposedActiveSourceContext\":null,"
                        + "\"citedEvidence\":[\"invented citation\"],\"warnings\":[],\"provider\":\"ollama\","
                        + "\"model\":\"qwen3:8b\",\"reasoning\":\"grounded\",\"stateDelta\":[],\"toolCalls\":[]}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        org.junit.jupiter.api.Assertions.assertThrows(GmAgentFailureException.class,
                () -> controller.plan("token", request()));
    }

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
    void malformed_response_retries_once_and_exposes_typed_safe_failure() {
        int[] calls = {0};
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                                   com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                calls[0]++;
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "raw provider payload must not escape");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var exception = assertThrows(GmAgentFailureException.class,
                () -> controller.plan("token", request()));

        org.junit.jupiter.api.Assertions.assertEquals(2, calls[0]);
        org.junit.jupiter.api.Assertions.assertEquals(GmFailureCategory.SCHEMA, exception.failure().category());
        org.junit.jupiter.api.Assertions.assertTrue(exception.failure().retryable());
        org.junit.jupiter.api.Assertions.assertEquals("turn", exception.failure().correlationId());
        org.junit.jupiter.api.Assertions.assertFalse(exception.failure().safeMessage().contains("raw provider"));
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
    void rejects_protected_fact_in_player_visible_provider_output() {
        var response = new GmAgentController.Response("scene", "npc", "judgment",
                "The crimson crown is behind the altar", null, List.of(), List.of(),
                "ollama", "qwen3:8b", "reasoning", List.of());
        var request = new GmAgentController.Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open", "scene", "npc", "", "",
                List.of(), List.of(), List.of(), List.of(), List.of(), "", "", "", "",
                List.of("hidden crimson crown"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GmResponseSafetyPolicy.rejectProtectedFacts(response.narration(), request.protectedFacts()));
    }

    @Test
    void rejects_citation_not_matching_selected_identity_and_excerpt() {
        var controller = new GmAgentController(null, new com.fasterxml.jackson.databind.ObjectMapper(), null);
        var selected = java.util.Map.of("type", "RULEBOOK", "knowledgeDocumentId", UUID.randomUUID(),
                "extractionVersion", 1, "locator", "page:1", "excerpt", "Exact rule");
        var request = new GmAgentController.Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open", "scene", "npc", "", "",
                List.of(selected), List.of(), List.of(), List.of(), List.of(), "", "", "", "", List.of());
        var response = new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                List.of(java.util.Map.of("type", "RULEBOOK", "knowledgeDocumentId", selected.get("knowledgeDocumentId"),
                        "extractionVersion", 1, "locator", "page:1", "excerpt", "Forged rule")),
                List.of(), "ollama", "qwen", "", List.of());
        var exception = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.validateCitations(response, request));
        org.junit.jupiter.api.Assertions.assertEquals("citation is outside selected evidence", exception.getMessage());
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

    @Test
    void quality_evaluation_uses_paraphrase_secret_gate_without_collision_false_positive() {
        var response = "{\"scene\":\"crypt\",\"npcState\":\"alert\",\"judgment\":\"success\","
                + "\"narration\":\"A crimson crown glints.\",\"citedEvidence\":[],"
                + "\"warnings\":[],\"provider\":\"ollama\",\"model\":\"qwen3:8b\","
                + "\"reasoning\":\"medium\",\"stateDelta\":[],\"toolCalls\":[]}";
        var service = new GmQualityEvaluationService(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper());

        var leak = service.evaluate(List.of(new GmQualityEvaluationService.Scenario(
                "secret-paraphrase", "Open", List.of(), List.of(), List.of("hidden crimson crown"), List.of(), 4.0))).getFirst();
        var collision = service.evaluate(List.of(new GmQualityEvaluationService.Scenario(
                "secret-collision", "Open", List.of(), List.of(), List.of("hidden emerald crown"), List.of(), 4.0))).getFirst();

        org.junit.jupiter.api.Assertions.assertTrue(leak.secretLeak());
        org.junit.jupiter.api.Assertions.assertFalse(collision.secretLeak());
    }

    private static GmAgentController.Request request() {
        return new GmAgentController.Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding",
                "", "", List.of(), List.of(), List.of(), List.of(), List.of(), "", "", "", "");
    }
}
