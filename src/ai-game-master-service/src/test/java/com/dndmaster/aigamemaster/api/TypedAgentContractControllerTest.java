package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionResult;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypedAgentContractControllerTest {
    private static final UUID SOLO_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final UUID SELECTED_ENDPOINT_ID = UUID.fromString("00000000-0000-0000-0000-000000000322");

    @Test
    void every_typed_agent_endpoint_requires_the_internal_service_token() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.scenarioCompilation("wrong", new TypedAgentContractController.ScenarioCompilationRequest(SOLO_PLAYER_ID, "op", "storybook")));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.scenarioLookup("wrong", new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "door", Map.of())));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.runtimeTurn("wrong", new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "open door", List.of())));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.narrationSafety("wrong", new TypedAgentContractController.NarrationSafetyRequest(SOLO_PLAYER_ID, "A door opens.", List.of())));
    }

    @Test
    void typed_requests_reject_missing_required_values_before_provider_access() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.scenarioLookup("service-secret", new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, " ", Map.of())));
    }

    @Test
    void runtime_turn_requires_the_server_confirmed_solo_player_id() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        assertThrows(NullPointerException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(null, "op", "look around", List.of(), Map.of())));
    }

    @Test
    void typed_lookup_compilation_and_safety_forward_the_server_confirmed_solo_player_id() {
        var seen = new java.util.ArrayList<UUID>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{}");
            }
            @Override public <T> T complete(UUID owner, String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                seen.add(owner);
                String response = operation.startsWith("scenario-compilation") ? "{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1}}"
                        : operation.startsWith("scenario-lookup") ? "{\"status\":\"NOT_FOUND\",\"answer\":\"\",\"supportingElementIds\":[]}"
                        : "{\"approved\":true}";
                return parser.parse(response);
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        controller.scenarioCompilation("service-secret", new TypedAgentContractController.ScenarioCompilationRequest(SOLO_PLAYER_ID, "scenario-compilation:op", "storybook"));
        controller.scenarioLookup("service-secret", new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "door", Map.of()));
        controller.narrationSafety("service-secret", new TypedAgentContractController.NarrationSafetyRequest(SOLO_PLAYER_ID, "문이 열린다.", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals(List.of(SOLO_PLAYER_ID, SOLO_PLAYER_ID, SOLO_PLAYER_ID), seen);
    }

    @Test
    void scenario_lookup_prompt_declares_the_json_contract_required_by_its_parser() {
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"status\":\"not_found\",\"answer\":\"\",\"supportingElementIds\":[]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.scenarioLookup("service-secret",
                new TypedAgentContractController.ScenarioLookupRequest(SOLO_PLAYER_ID, "opening", Map.of()));

        org.junit.jupiter.api.Assertions.assertEquals("NOT_FOUND", response.status());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("OUTPUT_CONTRACT"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("status must be FOUND or NOT_FOUND"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not use markdown, code fences, or any other text"));
    }

    @Test
    void scenario_compilation_prompt_requires_storybook_grounded_encounters_and_exact_sources() {
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"status\":\"READY\",\"scenarioModel\":{\"schemaVersion\":1}}");
            }
        };
        var controller = new TypedAgentContractController(adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        controller.scenarioCompilation("service-secret", new TypedAgentContractController.ScenarioCompilationRequest(
                SOLO_PLAYER_ID, "scenario-compilation:test", "DOCUMENT_ID=abc\\nEXTRACTION_VERSION=1\\nLOCATOR=page:2\\nTEXT=Eight Giant Rats begin combat."));

        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Find essential combat encounters"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Never invent or rewrite a source reference"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("scenarioModel.encounters"));
    }

    @Test
    void runtime_turn_prompt_declares_the_json_contract_required_by_its_parser() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<RequestedGmProviderSelection> selection = new AtomicReference<>();
        GmCompletionAdapter adapter = selectedAdapter((operation, value, requested) -> {
            prompt.set(value);
            selection.set(requested);
            return "{\"scene\":\"양조장\",\"judgment\":\"안전함\",\"narration\":\"방 안은 조용합니다.\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush") + ",\"combatStart\":true,\"mapEntryRequested\":true,\"combatEnemies\":[{\"scenarioId\":\"cellar-rat-ambush\",\"enemyKey\":\"giant-rat\",\"name\":\"거대 쥐\",\"count\":8}]}";
        });

        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        TypedAgentContractController.RuntimeTurnResponse response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING",
                        SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high", List.of(), Map.of()));

        org.junit.jupiter.api.Assertions.assertTrue(response.combatStart());
        org.junit.jupiter.api.Assertions.assertTrue(response.mapEntryRequested());
        org.junit.jupiter.api.Assertions.assertEquals("cellar-rat-ambush", response.combatEnemies().get(0).scenarioId());
        org.junit.jupiter.api.Assertions.assertEquals(8, response.combatEnemies().get(0).count());
        org.junit.jupiter.api.Assertions.assertEquals(new RequestedGmProviderSelection(SELECTED_ENDPOINT_ID, "openai", "gpt-5", "high"), selection.get());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("OUTPUT_CONTRACT"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("scene, judgment, narration, situation, combatStart, combatEnemies, mapEntryRequested, and optional runtimeFacts"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("MANDATORY: if a hostile creature"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("SESSION_OPENING"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("current location and why the party is here"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("LANGUAGE_CONTRACT"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("only in natural Korean"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not output English or any other foreign-language words"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("end with a Korean question"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not use markdown"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("executed dialogue action"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("RUNTIME_ADDED_FACTS contains durable facts"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("including confirmed combat outcomes"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("never narrate a defeated enemy as active again"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("NPC reaction"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("MANDATORY: when the player explicitly chooses to start or join a fight"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not repeat the same dialogue action"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("diegetic"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Game State, established Runtime-added Facts, locked Scenario Model, then Storybook RAG"));
    }

    @Test
    void runtime_turn_reads_optional_runtime_facts_from_the_typed_response() {
        var adapter = selectedAdapter((operation, value, requested) -> "{\"scene\":\"양조장\",\"judgment\":\"협상 가능\",\"narration\":\"글로우킨이 조건을 제안합니다.\",\"situation\":"
                + situation("SCENARIO", "cellar")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[],"
                + "\"runtimeFacts\":[{\"subject\":\"보상\",\"content\":\"글로우킨이 30골드를 제안했습니다.\"}]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "PLAYER_ACTION", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals(1, response.runtimeFacts().size());
        org.junit.jupiter.api.Assertions.assertEquals("보상", response.runtimeFacts().get(0).subject());
    }

    @Test
    void opening_rejects_player_visible_text_that_contains_a_foreign_language() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"양조장\",\"judgment\":\"안전함\","
                + "\"narration\":\"The room is quiet.\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING", List.of())));
    }

    @Test
    void opening_rejects_player_visible_text_without_korean_letters() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"양조장\",\"judgment\":\"안전함\","
                + "\"narration\":\"123 !!!\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "SESSION_OPENING", List.of())));
    }

    @Test
    void runtime_turn_rejects_a_missing_combat_start_decision() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"brewery\",\"judgment\":\"safe\",\"narration\":\"The room is quiet.\"}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_rejects_combat_without_a_structured_enemy_name() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"brewery\",\"judgment\":\"combat\",\"narration\":\"The door bursts open.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,\"combatEnemies\":[]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "look around", List.of())));
    }

    @Test
    void runtime_turn_accepts_an_explicit_instant_combat_mode_without_a_scenario_id() {
        GmCompletionAdapter adapter = selectedAdapter((operation, prompt, requested) -> "{\"scene\":\"cellar\",\"judgment\":\"critical failure\","
                + "\"narration\":\"The noise draws a giant rat.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,"
                + "\"combatEnemies\":[{\"mode\":\"INSTANT\",\"scenarioId\":\"\","
                + "\"enemyKey\":\"giant-rat\",\"name\":\"Giant Rat\",\"count\":1}]}");
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest(SOLO_PLAYER_ID, "op", "critical failure", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals("INSTANT", response.combatEnemies().get(0).mode());
        org.junit.jupiter.api.Assertions.assertEquals("", response.combatEnemies().get(0).scenarioId());
    }

    private static GmCompletionAdapter emptyAdapter() {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("provider must not be called by authorization tests");
            }
        };
    }

    private static GmCompletionAdapter selectedAdapter(SelectedJson response) {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt, StructuredResponseParser<T> parser) {
                throw new AssertionError("selected provider path must be used");
            }

            @Override
            public <T> GmCompletionResult<T> completeWithSelection(UUID soloPlayerId, String operation,
                    String prompt, StructuredResponseParser<T> parser, RequestedGmProviderSelection requested) {
                return new GmCompletionResult<>(parser.parse(response.json(operation, prompt, requested)), effectiveSelection(requested));
            }
        };
    }

    private static EffectiveGmProviderSelection effectiveSelection(RequestedGmProviderSelection requested) {
        UUID endpointId = requested.endpointId() == null ? SELECTED_ENDPOINT_ID : requested.endpointId();
        return new EffectiveGmProviderSelection(endpointId, Instant.EPOCH, requested.provider(), requested.model(), requested.reasoning());
    }

    @FunctionalInterface
    private interface SelectedJson {
        String json(String operation, String prompt, RequestedGmProviderSelection requested);
    }

    private static String situation(String basis, String reference) {
        return "{\"kind\":\"TRANSITION\",\"location\":\"지하 저장고\",\"problem\":\"위협을 찾기\","
                + "\"threat\":\"적대적인 생물\",\"goal\":\"안전 확보\",\"basis\":\"" + basis
                + "\",\"reference\":\"" + reference + "\",\"required\":true}";
    }
}
