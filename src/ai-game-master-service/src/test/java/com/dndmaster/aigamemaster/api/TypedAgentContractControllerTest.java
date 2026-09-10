package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TypedAgentContractControllerTest {
    @Test
    void every_typed_agent_endpoint_requires_the_internal_service_token() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.scenarioCompilation("wrong", new TypedAgentContractController.ScenarioCompilationRequest("op", "storybook")));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.scenarioLookup("wrong", new TypedAgentContractController.ScenarioLookupRequest("door", Map.of())));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.runtimeTurn("wrong", new TypedAgentContractController.RuntimeTurnRequest("op", "open door", List.of())));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.narrationSafety("wrong", new TypedAgentContractController.NarrationSafetyRequest("A door opens.", List.of())));
    }

    @Test
    void typed_requests_reject_missing_required_values_before_provider_access() {
        TypedAgentContractController controller = new TypedAgentContractController(
                emptyAdapter(), new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.scenarioLookup("service-secret", new TypedAgentContractController.ScenarioLookupRequest(" ", Map.of())));
    }

    @Test
    void runtime_turn_prompt_declares_the_json_contract_required_by_its_parser() {
        AtomicReference<String> prompt = new AtomicReference<>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                prompt.set(value);
                return parser.parse("{\"scene\":\"양조장\",\"judgment\":\"안전함\",\"narration\":\"방 안은 조용합니다.\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush") + ",\"combatStart\":true,\"mapEntryRequested\":true,\"combatEnemies\":[{\"scenarioId\":\"cellar-rat-ambush\",\"enemyKey\":\"giant-rat\",\"name\":\"거대 쥐\",\"count\":8}]}");
            }
        };

        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        TypedAgentContractController.RuntimeTurnResponse response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest("op", "SESSION_OPENING", List.of()));

        org.junit.jupiter.api.Assertions.assertTrue(response.combatStart());
        org.junit.jupiter.api.Assertions.assertTrue(response.mapEntryRequested());
        org.junit.jupiter.api.Assertions.assertEquals("cellar-rat-ambush", response.combatEnemies().get(0).scenarioId());
        org.junit.jupiter.api.Assertions.assertEquals(8, response.combatEnemies().get(0).count());
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
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Runtime Fact"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Canonical Fact"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("NPC reaction"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not repeat the same dialogue action"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("diegetic"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Game State, established Runtime-added Facts, locked Scenario Model, then Storybook RAG"));
    }

    @Test
    void runtime_turn_reads_optional_runtime_facts_from_the_typed_response() {
        var adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String value,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"양조장\",\"judgment\":\"협상 가능\",\"narration\":\"글로우킨이 조건을 제안합니다.\",\"situation\":"
                        + situation("SCENARIO", "cellar")
                        + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[],"
                        + "\"runtimeFacts\":[{\"subject\":\"보상\",\"content\":\"글로우킨이 30골드를 제안했습니다.\"}]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest("op", "PLAYER_ACTION", List.of()));

        org.junit.jupiter.api.Assertions.assertEquals(1, response.runtimeFacts().size());
        org.junit.jupiter.api.Assertions.assertEquals("보상", response.runtimeFacts().get(0).subject());
    }

    @Test
    void opening_rejects_player_visible_text_that_contains_a_foreign_language() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"양조장\",\"judgment\":\"안전함\","
                        + "\"narration\":\"The room is quiet.\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                        + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest("op", "SESSION_OPENING", List.of())));
    }

    @Test
    void opening_rejects_player_visible_text_without_korean_letters() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"양조장\",\"judgment\":\"안전함\","
                        + "\"narration\":\"123 !!!\",\"situation\":" + situation("SCENARIO", "cellar-rat-ambush")
                        + ",\"combatStart\":false,\"mapEntryRequested\":false,\"combatEnemies\":[]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest("op", "SESSION_OPENING", List.of())));
    }

    @Test
    void runtime_turn_rejects_a_missing_combat_start_decision() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"brewery\",\"judgment\":\"safe\",\"narration\":\"The room is quiet.\"}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest("op", "look around", List.of())));
    }

    @Test
    void runtime_turn_rejects_combat_without_a_structured_enemy_name() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"brewery\",\"judgment\":\"combat\",\"narration\":\"The door bursts open.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,\"combatEnemies\":[]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.runtimeTurn("service-secret",
                        new TypedAgentContractController.RuntimeTurnRequest("op", "look around", List.of())));
    }

    @Test
    void runtime_turn_accepts_an_explicit_instant_combat_mode_without_a_scenario_id() {
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"cellar\",\"judgment\":\"critical failure\","
                        + "\"narration\":\"The noise draws a giant rat.\",\"situation\":" + situation("FALLBACK", "") + ",\"combatStart\":true,\"mapEntryRequested\":false,"
                        + "\"combatEnemies\":[{\"mode\":\"INSTANT\",\"scenarioId\":\"\","
                        + "\"enemyKey\":\"giant-rat\",\"name\":\"Giant Rat\",\"count\":1}]}");
            }
        };
        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        var response = controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest("op", "critical failure", List.of()));

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

    private static String situation(String basis, String reference) {
        return "{\"kind\":\"TRANSITION\",\"location\":\"지하 저장고\",\"problem\":\"위협을 찾기\","
                + "\"threat\":\"적대적인 생물\",\"goal\":\"안전 확보\",\"basis\":\"" + basis
                + "\",\"reference\":\"" + reference + "\",\"required\":true}";
    }
}
