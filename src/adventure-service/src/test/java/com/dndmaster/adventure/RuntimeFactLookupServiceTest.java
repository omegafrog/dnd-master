package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.RuntimeFactLookupRequest;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupResult;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupService;
import com.dndmaster.adventure.application.runtime.ScenarioLookupResult;
import com.dndmaster.adventure.application.runtime.StorybookRagResult;
import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeFactLookupServiceTest {
    @Test
    void game_state_hit_short_circuits_runtime_model_and_rag_sources() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger ragCalls = new AtomicInteger();
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> { modelCalls.incrementAndGet(); return ScenarioLookupResult.notFound(); },
                request -> { ragCalls.incrementAndGet(); return StorybookRagResult.notFound(); });

        RuntimeFactLookupResult result = service.lookup(request("door", Map.of("door", "broken"),
                List.of(fact("the door was locked")), model("model-door")));

        assertEquals(RuntimeFactLookupResult.Status.FOUND, result.status());
        assertEquals(RuntimeFactLookupResult.Source.GAME_STATE, result.source());
        assertEquals("broken", result.answer());
        assertEquals(0, modelCalls.get());
        assertEquals(0, ragCalls.get());
    }

    @Test
    void runtime_added_fact_short_circuits_locked_model_and_rag() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger ragCalls = new AtomicInteger();
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> { modelCalls.incrementAndGet(); return ScenarioLookupResult.notFound(); },
                request -> { ragCalls.incrementAndGet(); return StorybookRagResult.notFound(); });

        RuntimeFactLookupResult result = service.lookup(request(Map.of(),
                List.of(fact("Mara is Harl's sister")), model("model-mara")));

        assertEquals(RuntimeFactLookupResult.Source.RUNTIME_ADDED_FACT, result.source());
        assertEquals("Mara is Harl's sister", result.answer());
        assertEquals(0, modelCalls.get());
        assertEquals(0, ragCalls.get());
    }

    @Test
    void locked_model_hit_short_circuits_storybook_rag() {
        AtomicInteger ragCalls = new AtomicInteger();
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.found("Harl fears the bell", List.of("model-harl")),
                request -> { ragCalls.incrementAndGet(); return StorybookRagResult.notFound(); });

        RuntimeFactLookupResult result = service.lookup(request(Map.of(), List.of(), model("model-harl")));

        assertEquals(RuntimeFactLookupResult.Source.SCENARIO_MODEL, result.source());
        assertEquals(List.of("model-harl"), result.supportingElementIds());
        assertEquals(0, ragCalls.get());
    }

    @Test
    void rag_is_used_only_after_higher_priority_sources_miss() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.notFound(),
                request -> StorybookRagResult.found("The bell is in the cellar"));

        RuntimeFactLookupResult result = service.lookup(request(Map.of(), List.of(), model("model-harl")));

        assertEquals(RuntimeFactLookupResult.Source.STORYBOOK_RAG, result.source());
        assertEquals("The bell is in the cellar", result.answer());
    }

    @Test
    void returns_not_found_without_inventing_a_fact() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.notFound(),
                request -> StorybookRagResult.notFound());

        RuntimeFactLookupResult result = service.lookup(request(Map.of(), List.of(), model("model-harl")));

        assertEquals(RuntimeFactLookupResult.Status.NOT_FOUND, result.status());
        assertEquals(RuntimeFactLookupResult.Source.NONE, result.source());
        assertTrue(result.answer().isBlank());
    }

    @Test
    void rejects_supporting_element_ids_outside_the_locked_model() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.found("invented", List.of("not-locked")),
                request -> StorybookRagResult.notFound());

        assertThrows(IllegalArgumentException.class,
                () -> service.lookup(request(Map.of(), List.of(), model("model-harl"))));
    }

    @Test
    void rejects_non_storybook_source_references_from_the_rag_boundary() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.notFound(),
                request -> StorybookRagResult.found("answer", List.of(
                        new com.dndmaster.adventure.application.runtime.RuntimeEvidence(
                                com.dndmaster.adventure.application.runtime.RuntimeEvidenceType.RULEBOOK,
                                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()),
                                1, "page:1", "rule"))));

        assertThrows(IllegalArgumentException.class,
                () -> service.lookup(request(Map.of(), List.of(), model("model-harl"))));
    }

    private static RuntimeFactLookupRequest request(Map<String, ?> state, List<RuntimeAddedFact> facts,
            ScenarioModel model) {
        return request("Harl's sister", state, facts, model);
    }

    private static RuntimeFactLookupRequest request(String query, Map<String, ?> state, List<RuntimeAddedFact> facts,
            ScenarioModel model) {
        return new RuntimeFactLookupRequest(query, new GameState(state, 3), facts, model);
    }

    private static RuntimeAddedFact fact(String content) {
        return new RuntimeAddedFact(UUID.randomUUID(), content, UUID.randomUUID());
    }

    private static ScenarioModel model(String elementId) {
        return new ScenarioModel(1,
                List.of(new ScenarioModelElement(elementId, "actor", Map.of("name", "Harl"), List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "The adventure begins.");
    }
}
