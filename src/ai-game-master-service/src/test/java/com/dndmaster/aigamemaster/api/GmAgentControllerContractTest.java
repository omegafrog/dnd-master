package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GmAgentControllerContractTest {
    @Test
    void generates_a_validated_companion_candidate_from_the_configured_agent() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"name\":\"브린\",\"race\":\"드워프\",\"characterClass\":\"파이터\",\"sheetSummary\":\"방패로 전열을 지키는 1레벨 전사.\"}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var candidate = controller.companionCandidate("token",
                new GmAgentController.CompanionCandidateRequest(UUID.randomUUID(), null, null, null));

        assertEquals("브린", candidate.name());
        assertEquals("파이터", candidate.characterClass());
    }

    @Test
    void rejects_incomplete_companion_candidate_output() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"name\":\"브린\"}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var error = assertThrows(ResponseStatusException.class, () -> controller.companionCandidate("token",
                new GmAgentController.CompanionCandidateRequest(UUID.randomUUID(), null, null, null)));
        assertEquals(503, error.getStatusCode().value());
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
    void accepts_complete_read_only_structured_response() {
        assertDoesNotThrow(() -> GmAgentController.requireComplete(
                new GmAgentController.Response("scene", "npc", "judgment", "narration", null,
                        List.of(), List.of(), "ollama", "qwen3:8b", "reasoning", List.of())));
    }

    @Test
    void normalizes_luna_empty_object_variants_without_allowing_state_mutation() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"opening\",\"npcState\":{},\"judgment\":\"choose\","
                        + "\"narration\":\"A bell rings.\",\"proposedActiveSourceContext\":null,"
                        + "\"citedEvidence\":[],\"warnings\":[],\"provider\":\"codex-cli\","
                        + "\"model\":\"gpt-5.6-luna\",\"reasoning\":\"none\",\"stateDelta\":{},"
                        + "\"toolCalls\":[],\"advanceStoryPlan\":{\"currentBeat\":\"opening\"}}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var response = controller.plan("token", request());

        assertEquals("opening", response.scene());
        assertEquals("gpt-5.6-luna", response.model());
    }

    @Test
    void preserves_explicit_story_plan_advance_for_a_known_branch() {
        var controller = controllerFor("true", "north");

        var response = controller.plan("token", request("availableBranches=north,south;"));

        assertEquals(true, response.advanceStoryPlan());
        assertEquals("north", response.selectedBranchId());
    }

    @Test
    void defaults_missing_or_false_story_plan_advance_to_false() {
        var controller = controllerFor("false", "north");

        var response = controller.plan("token", request("availableBranches=north;"));

        assertEquals(false, response.advanceStoryPlan());
        assertEquals("", response.selectedBranchId());

        response = controllerFor(null, "north").plan("token", request("availableBranches=north;"));
        assertEquals(false, response.advanceStoryPlan());
        assertEquals("", response.selectedBranchId());
    }

    @Test
    void rejects_story_plan_advance_for_an_unknown_branch() {
        var controller = controllerFor("true", "invented");

        var response = controller.plan("token", request("availableBranches=north;"));

        assertEquals(false, response.advanceStoryPlan());
        assertEquals("", response.selectedBranchId());
    }

    @Test
    void preserves_explicit_story_plan_advance_for_a_linear_stage() {
        var response = controllerFor("true", "").plan("token", request("availableBranches=;"));

        assertEquals(true, response.advanceStoryPlan());
        assertEquals("", response.selectedBranchId());
    }

    private static GmAgentController controllerFor(String advanceStoryPlan, String selectedBranchId) {
        return new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                String advanceField = advanceStoryPlan == null ? "" : "\"advanceStoryPlan\":" + advanceStoryPlan + ",";
                return parser.parse("{\"scene\":\"opening\",\"npcState\":\"alert\",\"judgment\":\"choose\","
                        + "\"narration\":\"A bell rings.\",\"proposedActiveSourceContext\":null,"
                        + "\"citedEvidence\":[],\"warnings\":[],\"provider\":\"codex-cli\","
                        + "\"model\":\"gpt-5.6-luna\",\"reasoning\":\"none\",\"stateDelta\":[],"
                        + "\"toolCalls\":[]," + advanceField
                        + "\"selectedBranchId\":\"" + selectedBranchId + "\"}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));
    }

    @Test
    void discards_citations_with_missing_or_unknown_evidence_type() {
        var controller = new GmAgentController(new com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"opening\",\"npcState\":\"alert\",\"judgment\":\"wait\","
                        + "\"narration\":\"The door waits.\",\"proposedActiveSourceContext\":null,"
                        + "\"citedEvidence\":[{\"knowledgeDocumentId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"extractionVersion\":1,\"locator\":\"page:1\",\"excerpt\":\"door\"}],"
                        + "\"warnings\":[],\"provider\":\"codex-cli\",\"model\":\"gpt-5.6-luna\","
                        + "\"reasoning\":\"none\",\"stateDelta\":[],\"toolCalls\":[]}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        var response = controller.plan("token", request());

        assertEquals(List.of(), response.citedEvidence());
        org.junit.jupiter.api.Assertions.assertTrue(response.warnings().stream()
                .anyMatch(warning -> warning.contains("invalid")));
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
        return request("");
    }

    private static GmAgentController.Request request(String storyPlanContext) {
        return new GmAgentController.Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding",
                "", "", List.of(), List.of(), List.of(), List.of(), List.of(), storyPlanContext, "", "", "");
    }
}
