package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class AdventureStoryPlanControllerMarkdownTest {
    @Test
    void preserves_supplied_map_identity_from_markdown_stage() {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        String markdown = """
                # Adventure Plan
                ## Stage 1: Cellar
                - Type: dungeon
                - Location: Beer Cellar
                - Goal: Clear the rats
                - Conflict: Giant rats ambush the party
                - Clear condition: The rats are defeated
                - Enemies: Giant Rats
                - Boss: none
                - Rewards: 25 gp
                - Branches: ending-1
                - Map definition ID: 859f67d2-1a4b-3679-b832-2c3ad59c6e9d
                - Map asset: page
                - Map locator: page
                ## Stage 2: Corridor
                - Type: event
                - Goal: Cross the corridor
                - Conflict: A mosaic trap blocks passage
                - Clear condition: The party crosses safely
                ## Stage 3: Return
                - Type: town
                - Goal: Return to Glowkindle
                - Conflict: Report the result
                - Clear condition: Payment is received
                """;

        var stages = controller.parseMarkdown(markdown,
                new AdventureStoryPlanController.Configuration(1, "SHORT"));

        assertEquals("859f67d2-1a4b-3679-b832-2c3ad59c6e9d", stages.getFirst().mapDefinitionId());
        assertEquals("page", stages.getFirst().mapAssetId());
        assertEquals("page", stages.getFirst().mapAssetLocator());
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
    void json_outline_rejects_omitted_required_collections_at_the_provider_boundary() throws Exception {
        var controller = new AdventureStoryPlanController(
                null, new ObjectMapper(), null,
                "http://127.0.0.1:11434", "unused", "codex", ".", Duration.ofMinutes(5),
                new ApiRequestGuard("test-internal-token"));
        String response = """
                {"stages":[
                  {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","endingIds":["ending-1"],"enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]},
                  {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]},
                  {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]}
                ]}
                """;
        var parse = AdventureStoryPlanController.class.getDeclaredMethod(
                "parse", String.class, AdventureStoryPlanController.Configuration.class);
        parse.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> parse.invoke(controller, response,
                        new AdventureStoryPlanController.Configuration(1, "SHORT")));

        assertEquals("npcOrClues must be explicit", deepestMessage(failure));
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
