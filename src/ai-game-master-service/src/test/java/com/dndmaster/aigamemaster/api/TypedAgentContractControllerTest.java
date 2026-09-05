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
                return parser.parse("{\"scene\":\"brewery\",\"judgment\":\"safe\",\"narration\":\"The room is quiet.\"}");
            }
        };

        TypedAgentContractController controller = new TypedAgentContractController(
                adapter, new ObjectMapper(), new ApiRequestGuard("service-secret"));

        controller.runtimeTurn("service-secret",
                new TypedAgentContractController.RuntimeTurnRequest("op", "look around", List.of()));

        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("OUTPUT_CONTRACT"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("scene, judgment, narration"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.get().contains("Do not use markdown"));
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
}
