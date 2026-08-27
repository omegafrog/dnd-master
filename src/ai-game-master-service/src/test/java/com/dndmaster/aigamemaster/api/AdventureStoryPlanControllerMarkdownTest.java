package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointStore;
import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

class AdventureStoryPlanControllerMarkdownTest {
    @Test
    void accepts_only_verified_json_outline_at_the_provider_boundary() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var parse = AdventureStoryPlanController.class.getDeclaredMethod(
                "parseJson", String.class, AdventureStoryPlanController.Configuration.class);
        parse.setAccessible(true);
        String response = """
                {"stages":[
                  {"position":1,"title":"맥주 저장고","goal":"쥐를 물리친다","conflict":"쥐가 습격한다","transitionCondition":"쥐를 물리친다","npcOrClues":[],"endingIds":["ending-1"],"stageType":"DUNGEON","location":"저장고","mapDefinitionId":"859f67d2-1a4b-3679-b832-2c3ad59c6e9d","mapAssetId":"page","mapAssetLocator":"page","enemies":["거대 쥐"],"boss":"","clearCondition":"쥐를 물리친다","failureCondition":"","rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]},
                  {"position":2,"title":"귀환","goal":"마을로 돌아간다","conflict":"길이 막힌다","transitionCondition":"길을 찾는다","npcOrClues":[],"endingIds":["ending-1"],"stageType":"TOWN","location":"양조장","mapDefinitionId":"","mapAssetId":"","mapAssetLocator":"","enemies":[],"boss":"","clearCondition":"길을 찾는다","failureCondition":"","rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]},
                  {"position":3,"title":"결말","goal":"의뢰를 보고한다","conflict":"보상을 확인한다","transitionCondition":"보고를 마친다","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"양조장","mapDefinitionId":"","mapAssetId":"","mapAssetLocator":"","enemies":[],"boss":"","clearCondition":"보고를 마친다","failureCondition":"","rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]}
                ]}
                """;
        var stages = (List<AdventureStoryPlanController.Stage>) parse.invoke(controller, response,
                new AdventureStoryPlanController.Configuration(1, "SHORT"));
        assertEquals("859f67d2-1a4b-3679-b832-2c3ad59c6e9d", stages.getFirst().mapDefinitionId());
        assertEquals("TOWN", stages.get(1).stageType());
    }

    @Test
    void tactical_scene_generation_requires_internal_token() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("service-secret"));

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.generateTacticalScene("wrong", new ObjectMapper().createObjectNode()));
    }

    @Test
    void story_plan_generation_requires_the_configured_internal_token() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("production-secret"));

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.generate("wrong-token", null));
    }

    @Test
    void verifier_accepts_only_pass_or_fail_with_violations() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var parse = AdventureStoryPlanController.class.getDeclaredMethod("parseVerificationResponse", String.class);
        parse.setAccessible(true);

        var pass = (AdventureStoryPlanController.VerificationResponse) parse.invoke(controller,
                "{\"status\":\"PASS\",\"violations\":[]}");
        assertEquals("PASS", pass.status());
        assertEquals(List.of(), pass.violations());

        var fail = (AdventureStoryPlanController.VerificationResponse) parse.invoke(controller,
                "{\"status\":\"FAIL\",\"violations\":[\"맵 참조가 없습니다\"]}");
        assertEquals("FAIL", fail.status());
        assertEquals(List.of("맵 참조가 없습니다"), fail.violations());
    }

    @Test
    void verifier_rejects_pass_with_violations() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var parse = AdventureStoryPlanController.class.getDeclaredMethod("parseVerificationResponse", String.class);
        parse.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> parse.invoke(controller, "{\"status\":\"PASS\",\"violations\":[\"invalid\"]}"));
        assertEquals("PASS verification must have no violations", deepestMessage(failure));
    }

    @Test
    void verifier_prompt_treats_triggers_as_conditional_requirements() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var promptMethod = AdventureStoryPlanController.class.getDeclaredMethod(
                "verificationDecisionPrompt", AdventureStoryPlanController.Request.class,
                AdventureStoryPlanController.Configuration.class, String.class);
        promptMethod.setAccessible(true);
        var request = new AdventureStoryPlanController.Request(
                "op", 1L, 1, AdventureStoryPlanController.Configuration.defaults(),
                List.of(), List.of());

        String prompt = (String) promptMethod.invoke(controller, request,
                AdventureStoryPlanController.Configuration.defaults(), "계획");

        assertTrue(prompt.contains("A stage without hidden information, a conditional event, or a rules check may have no trigger"));
        assertTrue(prompt.contains("Every hidden-information trigger, secret, clue reveal, conditional event, or rules check"));
        assertTrue(prompt.contains("explicit failure or fail-forward consequence"));
        assertTrue(prompt.contains("heading names, Markdown formatting"));
    }

    @Test
    void returns_structured_candidate_violations_for_retry() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));

        var response = controller.candidateValidation(
                new AdventureStoryPlanController.CandidateResponseValidationException(
                        List.of("Stage 1에 성공 결과가 없습니다", "Stage 1에 실패 결과가 없습니다"), null));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(List.of("Stage 1에 성공 결과가 없습니다", "Stage 1에 실패 결과가 없습니다"),
                response.getBody().violations());
    }

    @Test
    void repair_contract_requires_full_candidate_and_lists_authoritative_registries() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null, "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var violation = new AdventureStoryPlanController.ProjectionViolation(
                "INVALID_TRANSITION_CONDITION", 1, "stages[0].transitionCondition", "bad", "citation-1",
                AdventureStoryPlanController.ProjectionViolation.Repairability.REPAIRABLE,
                "transitionCondition is not usable");
        var request = new AdventureStoryPlanController.RepairRequest("op", 1, 1,
                new AdventureStoryPlanController.Configuration(1, "SHORT"),
                new ObjectMapper().readTree("{\"stages\":[{\"position\":1}]}"), List.of(violation),
                List.of("storybook.pdf"), List.of("source"), List.of(), List.of());
        var promptMethod = AdventureStoryPlanController.class.getDeclaredMethod(
                "repairPrompt", AdventureStoryPlanController.RepairRequest.class,
                AdventureStoryPlanController.Configuration.class);
        promptMethod.setAccessible(true);

        String prompt = (String) promptMethod.invoke(controller, request, request.configuration());

        assertTrue(prompt.contains("COMPLETE projection JSON object"));
        assertTrue(prompt.contains("stages[0].transitionCondition"));
        assertTrue(prompt.contains("previousFullCandidate"));
        assertTrue(prompt.contains("authoritativeCitations"));
    }

    @Test
    void rejected_projection_error_preserves_structured_diagnostics_without_using_candidate_as_message() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null, "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var candidate = new ObjectMapper().createObjectNode().put("secret", "candidate-content");
        var failure = new AdventureStoryPlanController.CandidateResponseValidationException(
                List.of(new AdventureStoryPlanController.ProjectionViolation(
                        "INVALID_TRANSITION_CONDITION", 1, "stages[0].transitionCondition", "bad", "",
                        AdventureStoryPlanController.ProjectionViolation.Repairability.REPAIRABLE,
                        "transitionCondition is not usable")), null, candidate);

        var response = controller.candidateValidation(failure);

        assertEquals("candidate-content", response.getBody().rejectedCandidate().path("secret").asText());
        assertEquals("stages[0].transitionCondition", response.getBody().structuredViolations().getFirst().fieldPath());
        assertEquals("bad", response.getBody().structuredViolations().getFirst().rejectedValue());
        assertEquals("", response.getBody().structuredViolations().getFirst().citationContext());
        assertTrue(!response.getBody().violations().getFirst().contains("candidate-content"));
    }

    @Test
    void structured_diagnostic_messages_are_bounded_and_single_line() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null, "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var raw = "unsafe source quote\n" + "x".repeat(400);
        var response = controller.candidateValidation(
                new AdventureStoryPlanController.CandidateResponseValidationException(
                        List.of(raw), null));

        String diagnostic = response.getBody().violations().getFirst();
        assertTrue(!diagnostic.contains("\n"));
        assertTrue(diagnostic.length() <= 259);
        assertTrue(diagnostic.endsWith("..."));
    }

    @Test
    void controller_diagnostics_preserve_stage_and_field_specificity() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null, "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var response = controller.candidateValidation(
                new AdventureStoryPlanController.CandidateResponseValidationException(
                        List.of("Stage 2 clearCondition is not usable"), null));

        var violation = response.getBody().structuredViolations().getFirst();
        assertEquals(2, violation.stagePosition());
        assertEquals("stages[1].clearCondition", violation.fieldPath());
    }

    @Test
    void json_outline_rejects_omitted_required_core_fields_at_the_provider_boundary() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        String response = """
                {"stages":[
                  {"position":1,"title":"Start","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]},
                  {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]},
                  {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]}
                ]}
                """;
        var parse = AdventureStoryPlanController.class.getDeclaredMethod(
                "parseJson", String.class, AdventureStoryPlanController.Configuration.class);
        parse.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> parse.invoke(controller, response,
                        new AdventureStoryPlanController.Configuration(1, "SHORT")));

        assertEquals("goal missing", deepestMessage(failure));
    }

    @Test
    void json_outline_requires_position_but_tolerates_optional_and_additional_fields() throws Exception {
        var controller = new AdventureStoryPlanController(null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        String valid = """
                {"stages":[
                  {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"optionalObject":{"nested":true},"unknownField":[1,2]},
                  {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"]},
                  {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","endingIds":["ending-1"]}
                ]}
                """;
        var parse = AdventureStoryPlanController.class.getDeclaredMethod("parseJson", String.class,
                AdventureStoryPlanController.Configuration.class);
        parse.setAccessible(true);
        assertEquals(3, ((List<?>) parse.invoke(controller, valid,
                new AdventureStoryPlanController.Configuration(1, "SHORT"))).size());

        String missingPosition = valid.replace("\"position\":1,", "");
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> parse.invoke(controller, missingPosition,
                        new AdventureStoryPlanController.Configuration(1, "SHORT")));
        assertEquals("position missing", deepestMessage(failure));
    }

    @Test
    void projection_prompt_describes_structured_required_fields_and_retry_context() throws Exception {
        var controller = new AdventureStoryPlanController(null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var method = AdventureStoryPlanController.class.getDeclaredMethod("projectionPrompt",
                AdventureStoryPlanController.Request.class, AdventureStoryPlanController.Configuration.class, String.class);
        method.setAccessible(true);
        var request = new AdventureStoryPlanController.Request("op", 1L, 1,
                new AdventureStoryPlanController.Configuration(1, "SHORT"), List.of(), List.of());
        String prompt = (String) method.invoke(controller, request, request.configuration(), "계획");
        assertTrue(prompt.contains("JSON shape constraint"));
        assertTrue(prompt.contains("non-empty array of strings"));
        assertTrue(prompt.contains("citationKey"));
        assertTrue(prompt.contains("Every sourceFactClaims item MUST be an object with non-empty fieldPath, normalizedClaim, and citationKeys"));
        assertTrue(prompt.contains("combatSkeleton.rewards MUST be an array of the same claim objects"));
        assertTrue(prompt.contains("previousViolations"));
    }

    @Test
    void execution_projection_parses_citation_keys_without_copying_source_locators() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var parse = AdventureStoryPlanController.class.getDeclaredMethod(
                "parseJson", String.class, AdventureStoryPlanController.Configuration.class);
        parse.setAccessible(true);
        String response = """
                {"stages":[
                  {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"evidence":[{"citationKey":"citation-1","documentType":"RULEBOOK","locator":"tampered","quote":"tampered"}]},
                  {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"evidence":[{"citationKey":"citation-1"}]},
                  {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","endingIds":["ending-1"],"evidence":[{"citationKey":"citation-1"}]}
                ]}
                """;

        var stages = (List<AdventureStoryPlanController.Stage>) parse.invoke(controller, response,
                new AdventureStoryPlanController.Configuration(1, "SHORT"));

        assertEquals("citation-1", stages.getFirst().evidence().getFirst().citationKey());
    }

    @Test
    void execution_projection_rejects_blank_citation_keys_instead_of_dropping_them() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        var parse = AdventureStoryPlanController.class.getDeclaredMethod(
                "parseJson", String.class, AdventureStoryPlanController.Configuration.class);
        parse.setAccessible(true);
        String response = """
                {"stages":[
                  {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"evidence":[{"citationKey":"   "}]},
                  {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"evidence":[]},
                  {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","endingIds":["ending-1"],"evidence":[]}
                ]}
                """;

        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> parse.invoke(controller, response, new AdventureStoryPlanController.Configuration(1, "SHORT")));

        assertEquals("citationKey missing", deepestMessage(failure));
    }

    @Test
    void request_citation_key_is_preserved_for_the_model_registry_prompt() throws Exception {
        var mapper = new ObjectMapper();
        var request = mapper.readValue("""
                {"operationId":"op","packageRevision":1,"partySize":1,
                 "configuration":{"endingCount":1,"adventureLength":"SHORT"},
                 "sourceDocuments":[],"resolutionEvidence":[],"maps":[],
                 "citations":[{"documentType":"STORYBOOK","documentId":"doc","extractionVersion":2,
                 "locator":"page=1","quote":"source","confidence":0.9,"citationKey":"citation-1"}],
                 "violations":[],"previousCandidate":""}
                """, AdventureStoryPlanController.Request.class);

        assertEquals("citation-1", request.citations().getFirst().citationKey());
    }

    @Test
    void malformed_tactical_candidate_is_reported_as_unprocessable_content() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("not-json")))));
        var adapter = new SpringAiChatAdapter(model, 1, new SafeAiAuditLogger(ignored -> { }));
        var endpoint = new AgentEndpoint(
                UUID.randomUUID(), "local", AgentEndpoint.Provider.OLLAMA,
                URI.create("http://127.0.0.1:11434"), "qwen", null, true, Instant.now());
        var store = mock(AgentEndpointStore.class);
        when(store.active()).thenReturn(Optional.of(endpoint));
        var registry = new AgentEndpointRegistry(store);
        var controller = new AdventureStoryPlanController(
                adapter, new ObjectMapper(), registry,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));

        AdventureStoryPlanController.CandidateResponseValidationException failure = assertThrows(
                AdventureStoryPlanController.CandidateResponseValidationException.class,
                () -> controller.generateTacticalScene(
                        "test-internal-token", new ObjectMapper().createObjectNode()));

        assertEquals("tactical candidate is not valid JSON", failure.violations().getFirst());
    }

    @Test
    void mockmvc_exposes_controller_candidate_validation_to_the_typed_422_handler() throws Exception {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/api/generate"))
                    .inScenario("story-plan")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(okJson(new ObjectMapper().writeValueAsString(Map.of("response", "x".repeat(600)))))
                    .willSetStateTo("verification"));
            server.stubFor(post(urlEqualTo("/api/generate"))
                    .inScenario("story-plan")
                    .whenScenarioStateIs("verification")
                    .willReturn(okJson(new ObjectMapper().writeValueAsString(Map.of("response", "{\"status\":\"PASS\",\"violations\":[]}"))))
                    .willSetStateTo("projection"));
            server.stubFor(post(urlEqualTo("/api/generate"))
                    .inScenario("story-plan")
                    .whenScenarioStateIs("projection")
                    .willReturn(okJson(new ObjectMapper().writeValueAsString(Map.of("response", "{\"stages\":[]}")))));

            var endpoint = new AgentEndpoint(UUID.randomUUID(), "wiremock", AgentEndpoint.Provider.OLLAMA,
                    URI.create(server.baseUrl()), "test-model", null, true, Instant.now());
            var store = mock(AgentEndpointStore.class);
            when(store.active()).thenReturn(Optional.of(endpoint));
            var controller = new AdventureStoryPlanController(null, new ObjectMapper(), new AgentEndpointRegistry(store),
                    server.baseUrl(), "unused", "codex", ".", Duration.ofMinutes(5),
                    new ApiRequestGuard("test-internal-token"));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                            "/internal/v1/gm/adventure-story-plan")
                    .header("X-Internal-Token", "test-internal-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"operationId":"op","packageRevision":1,"partySize":1,
                             "configuration":{"endingCount":1,"adventureLength":"SHORT"},
                             "sourceDocuments":[],"resolutionEvidence":[],"maps":[],"citations":[],
                             "violations":[],"previousCandidate":""}
                            """))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.violations[0]").value("invalid stage count"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rejectedCandidate.stages").isArray())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.structuredViolations[0].repairability").value("REGENERATE_REQUIRED"));
        } finally {
            server.stop();
        }
    }

    private static String deepestMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage();
    }
}
