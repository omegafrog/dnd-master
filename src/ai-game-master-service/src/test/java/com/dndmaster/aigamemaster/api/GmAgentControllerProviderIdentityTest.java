package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void v2_repairs_one_malformed_candidate_with_the_same_selection_and_evidence_pack() {
        UUID endpointId = UUID.randomUUID();
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(endpointId,
                Instant.parse("2026-08-27T01:02:03Z"), "ollama", "qwen3:8b", "medium");
        java.util.List<String> prompts = new java.util.ArrayList<>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GmAgentController controller = new GmAgentController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("v2 must use the selected lifecycle method");
            }

            @Override public <T> GmCompletionResult<T> completeWithSelection(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser,
                    RequestedGmProviderSelection requested) {
                prompts.add(prompt);
                String json = calls.getAndIncrement() == 0 ? "{}" :
                        "{\"scene\":\"crypt\",\"npcState\":\"alert\",\"judgment\":\"성공\","
                                + "\"narration\":\"문이 열립니다.\",\"proposedActiveSourceContext\":null,"
                                + "\"citedEvidence\":[],\"warnings\":[],\"provider\":\"ollama\","
                                + "\"model\":\"qwen3:8b\",\"reasoning\":\"medium\",\"stateDelta\":[],\"toolCalls\":[]}";
                return new GmCompletionResult<>(parser.parse(json), effective);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        GmAgentController.V2Response response = controller.planV2("token", request(
                new RequestedGmProviderSelection(endpointId, "ollama", "qwen3:8b", "medium")));

        assertEquals(2, response.attemptCount());
        assertEquals(effective.provider(), response.effectiveSelection().provider());
        assertEquals(effective.endpointVersion(), response.effectiveSelection().endpointVersion());
        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).contains("storybook=[]"));
        assertTrue(prompts.get(1).contains("initialCandidate"));
        assertTrue(prompts.get(1).contains("GM_REQUIRED_FIELD_MISSING"));
    }

    @Test
    void v2_exhausted_repair_is_retryable_and_never_uses_semantic_defaults() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GmAgentController controller = new GmAgentController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("v2 must use the selected lifecycle method");
            }

            @Override public <T> GmCompletionResult<T> completeWithSelection(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser,
                    RequestedGmProviderSelection requested) {
                calls.incrementAndGet();
                return new GmCompletionResult<>(parser.parse("{}"), new EffectiveGmProviderSelection(
                        UUID.randomUUID(), Instant.parse("2026-08-27T01:02:03Z"), "ollama", "qwen3:8b", "none"));
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.planV2("token", request(new RequestedGmProviderSelection(
                        UUID.randomUUID(), "ollama", "qwen3:8b", "none"))));

        assertEquals(503, error.getStatusCode().value());
        assertEquals(2, calls.get());
        assertEquals("GM provider unavailable", error.getReason());
    }

    @Test
    void legacy_parser_rejects_missing_judgment_instead_of_inventing_one() {
        GmAgentController controller = new GmAgentController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                return parser.parse("{\"scene\":\"crypt\",\"npcState\":\"alert\","
                        + "\"narration\":\"문이 열립니다.\",\"citedEvidence\":[],\"warnings\":[],"
                        + "\"provider\":\"ollama\",\"model\":\"qwen3:8b\",\"reasoning\":\"none\","
                        + "\"stateDelta\":[],\"toolCalls\":[]}");
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.plan("token", legacyRequest()));

        assertEquals(503, error.getStatusCode().value());
    }

    @Test
    void v2_repairs_missing_storybook_citation_and_keeps_the_pack_bounded() {
        UUID documentId = UUID.randomUUID();
        java.util.Map<String, Object> evidence = java.util.Map.of(
                "type", "STORYBOOK", "knowledgeDocumentId", documentId.toString(),
                "extractionVersion", 2L, "locator", "page:4", "excerpt", "A locked crypt door.");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(UUID.randomUUID(),
                Instant.parse("2026-08-27T01:02:03Z"), "ollama", "qwen3:8b", "none");
        GmAgentController controller = new GmAgentController(new GmCompletionAdapter() {
            @Override public <T> T complete(String operation, String prompt,
                                             com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser) {
                throw new AssertionError("v2 must use the selected lifecycle method");
            }

            @Override public <T> GmCompletionResult<T> completeWithSelection(String operation, String prompt,
                    com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<T> parser,
                    RequestedGmProviderSelection requested) {
                String citation = calls.getAndIncrement() == 0 ? "[]" :
                        "[{\"type\":\"STORYBOOK\",\"knowledgeDocumentId\":\"" + documentId
                                + "\",\"extractionVersion\":2,\"locator\":\"page:4\",\"excerpt\":\"A locked crypt door.\"}]";
                return new GmCompletionResult<>(parser.parse("{\"scene\":\"crypt\",\"npcState\":\"alert\","
                        + "\"judgment\":\"성공\",\"narration\":\"문이 열립니다.\","
                        + "\"proposedActiveSourceContext\":null,\"citedEvidence\":" + citation
                        + ",\"warnings\":[],\"provider\":\"ollama\",\"model\":\"qwen3:8b\","
                        + "\"reasoning\":\"none\",\"stateDelta\":[],\"toolCalls\":[]}"), effective);
            }
        }, new com.fasterxml.jackson.databind.ObjectMapper(), new ApiRequestGuard("token"));

        GmAgentController.V2Response response = controller.planV2("token", requestWithEvidence(
                new RequestedGmProviderSelection(UUID.randomUUID(), "ollama", "qwen3:8b", "none"), evidence));

        assertEquals(2, response.attemptCount());
        assertEquals(1, response.candidate().citedEvidence().size());
    }

    private static GmAgentController.V2Request request(RequestedGmProviderSelection selection) {
        return new GmAgentController.V2Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding", "", "",
                List.of(), List.of(), List.of(), List.of(), List.of(), "", selection);
    }

    private static GmAgentController.V2Request requestWithEvidence(RequestedGmProviderSelection selection, Object evidence) {
        return new GmAgentController.V2Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding", "", "",
                List.of(evidence), List.of(), List.of(), List.of(), List.of(), "", selection);
    }

    private static GmAgentController.Request legacyRequest() {
        return new GmAgentController.Request("turn", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "capability", "open the door", "crypt", "guarding",
                "", "", List.of(), List.of(), List.of(), List.of(), List.of(), "", "", "", "");
    }
}
