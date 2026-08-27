package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionResult;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GmAgentControllerProviderIdentityTest {
    @Test
    void v2_response_keeps_requested_and_effective_selection_separate() {
        UUID endpointId = UUID.randomUUID();
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(endpointId,
                Instant.parse("2026-08-27T01:02:03Z"), "ollama", "qwen3:8b", "medium");
        GmAgentController controller = new GmAgentController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("v1 adapter method must not be used by v2");
            }

            @Override public <T> GmCompletionResult<T> completeWithSelection(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser,
                    RequestedGmProviderSelection requested) {
                T candidate = parser.parse("{\"scene\":\"crypt\",\"npcState\":\"alert\",\"judgment\":\"success\","
                        + "\"narration\":\"문이 열립니다.\",\"citedEvidence\":[],\"warnings\":[],"
                        + "\"provider\":\"openai\",\"model\":\"requested-model\",\"reasoning\":\"medium\","
                        + "\"stateDelta\":[],\"toolCalls\":[]}");
                return new GmCompletionResult<>(candidate, effective);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        GmAgentController.V2Response response = controller.planV2("token", request(
                new RequestedGmProviderSelection(endpointId, "openai", "requested-model", "medium")));

        assertEquals("openai", response.requestedSelection().provider());
        assertEquals("ollama", response.effectiveSelection().provider());
        assertEquals("qwen3:8b", response.effectiveSelection().model());
        assertEquals(1, response.attemptCount());
    }

    @Test
    void v2_selection_failure_is_a_structured_conflict_without_calling_the_provider() {
        GmAgentController controller = new GmAgentController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("provider must not be called");
            }

            @Override public <T> GmCompletionResult<T> completeWithSelection(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser,
                    RequestedGmProviderSelection requested) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionUnresolvedException(requested);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.planV2("token", request(new RequestedGmProviderSelection(
                        UUID.randomUUID(), "ollama", "qwen3:8b", "none"))));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("GM_PROVIDER_SELECTION_UNRESOLVED", error.getReason());
    }

    private static GmAgentController.V2Request request(RequestedGmProviderSelection selection) {
        return new GmAgentController.V2Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding", "", "",
                List.of(), List.of(), List.of(), List.of(), List.of(), "", selection);
    }
}
