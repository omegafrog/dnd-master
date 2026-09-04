package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
