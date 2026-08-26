package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
        assertTrue(prompt.contains("previousViolations"));
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

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.generateTacticalScene(
                        "test-internal-token", new ObjectMapper().createObjectNode()));

        assertEquals(422, failure.getStatusCode().value());
    }

    private static String deepestMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage();
    }
}
