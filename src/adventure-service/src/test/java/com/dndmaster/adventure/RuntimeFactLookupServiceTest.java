package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.RuntimeFactLookupRequest;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupResult;
import com.dndmaster.adventure.application.runtime.RuntimeFactLookupService;
import com.dndmaster.adventure.application.runtime.ScenarioLookupResult;
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
    void immutable_document_ids_include_source_version_and_locator() {
        var evidence = new com.dndmaster.adventure.application.runtime.RuntimeEvidence(
                com.dndmaster.adventure.application.runtime.RuntimeEvidenceType.STORYBOOK,
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(
                        UUID.fromString("00000000-0000-0000-0000-000000000001")), 2, "page:1", "excerpt");

        assertEquals("IMMUTABLE_DOCUMENT:STORYBOOK:00000000-0000-0000-0000-000000000001:2:page:1",
                evidence.immutableEvidenceId());
    }

    @Test
    void game_state_hit_short_circuits_isolated_lookup() {
        AtomicInteger modelCalls = new AtomicInteger();
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> { modelCalls.incrementAndGet(); return ScenarioLookupResult.notFound(); });

        RuntimeFactLookupResult result = service.lookup(request("door", Map.of("door", "broken"),
                List.of(fact("the door was locked")), model("model-door")));

        assertEquals(RuntimeFactLookupResult.Status.FOUND, result.status());
        assertEquals(RuntimeFactLookupResult.Source.GAME_STATE, result.source());
        assertEquals("broken", result.answer());
        assertEquals(0, modelCalls.get());
    }

    @Test
    void runtime_added_fact_short_circuits_isolated_lookup() {
        AtomicInteger modelCalls = new AtomicInteger();
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> { modelCalls.incrementAndGet(); return ScenarioLookupResult.notFound(); });

        RuntimeFactLookupResult result = service.lookup(request(Map.of(),
                List.of(fact("Mara is Harl's sister")), model("model-mara")));

        assertEquals(RuntimeFactLookupResult.Source.RUNTIME_ADDED_FACT, result.source());
        assertEquals("Mara is Harl's sister", result.answer());
        assertEquals(0, modelCalls.get());
    }

    @Test
    void passes_the_full_action_to_semantic_scenario_lookup_instead_of_extracting_a_subject_word() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> {
                    assertEquals("I ask the keeper what the reward is", request.query());
                    assertEquals("the keeper's office", request.currentSituation());
                    assertEquals("North Market", request.currentScene());
                    assertEquals(Map.of("door", "broken"), request.establishedGameState());
                    assertEquals("The bell rings at dusk", request.runtimeAddedFacts().getFirst().content());
                    assertEquals(1, request.evidence().size());
                    assertEquals("STORYBOOK", request.evidence().getFirst().evidenceType().name());
                    assertEquals("prepared situation: warehouse guard", request.preparedSituation());
                    assertEquals("The guard knows Mara", request.longTermFacts().getFirst().relevance());
                    assertEquals(List.of("IMMUTABLE_DOCUMENT:STORYBOOK:00000000-0000-0000-0000-000000000001:2:p:1"),
                            request.knownEvidenceIds());
                    return ScenarioLookupResult.found("The keeper offers 20 gold pieces.", List.of("model-reward"));
                });

        RuntimeFactLookupResult result = service.lookup(new RuntimeFactLookupRequest(
                "I ask the keeper what the reward is", new GameState(Map.of("door", "broken"), 3),
                List.of(fact("The bell rings at dusk")), model("model-reward"), "the keeper's office", "North Market",
                List.of(evidence(com.dndmaster.adventure.application.runtime.RuntimeEvidenceType.STORYBOOK)),
                "prepared situation: warehouse guard", List.of(new com.dndmaster.adventure.application.runtime.LongTermAdventureFact(
                        new com.dndmaster.adventure.domain.adventure.AdventureId(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(),
                        1, "NPC fact", "The guard knows Mara", true, 1)),
                List.of("IMMUTABLE_DOCUMENT:STORYBOOK:00000000-0000-0000-0000-000000000001:2:p:1")));

        assertEquals(RuntimeFactLookupResult.Source.SCENARIO_MODEL, result.source());
        assertEquals("The keeper offers 20 gold pieces.", result.answer());
    }

    @Test
    void locked_model_hit_returns_before_more_lookup() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.found("Harl fears the bell", List.of("model-harl")));

        RuntimeFactLookupResult result = service.lookup(request(Map.of(), List.of(), model("model-harl")));

        assertEquals(RuntimeFactLookupResult.Source.SCENARIO_MODEL, result.source());
        assertEquals(List.of("model-harl"), result.supportingElementIds());
    }

    @Test
    void not_found_is_returned_after_locked_model_misses_without_a_second_book_search() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.notFound());

        RuntimeFactLookupResult result = service.lookup(request(Map.of(), List.of(), model("model-harl")));

        assertEquals(RuntimeFactLookupResult.Source.NONE, result.source());
        assertTrue(result.answer().isBlank());
    }

    @Test
    void returns_not_found_without_inventing_a_fact() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.notFound());

        RuntimeFactLookupResult result = service.lookup(request(Map.of(), List.of(), model("model-harl")));

        assertEquals(RuntimeFactLookupResult.Status.NOT_FOUND, result.status());
        assertEquals(RuntimeFactLookupResult.Source.NONE, result.source());
        assertTrue(result.answer().isBlank());
    }

    @Test
    void rejects_supporting_element_ids_outside_the_locked_model() {
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> ScenarioLookupResult.found("invented", List.of("not-locked")));

        assertThrows(IllegalArgumentException.class,
                () -> service.lookup(request(Map.of(), List.of(), model("model-harl"))));
    }

    @Test
    void passes_story_and_rule_evidence_to_the_same_isolated_lookup() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeFactLookupService service = new RuntimeFactLookupService(
                request -> {
                    calls.incrementAndGet();
                    assertEquals(2, request.evidence().size());
                    assertEquals(List.of("STORYBOOK", "RULEBOOK"), request.evidence().stream()
                            .map(item -> item.evidenceType().name()).toList());
                    return ScenarioLookupResult.notFound();
                });

        service.lookup(new RuntimeFactLookupRequest("inspect the altar", GameState.empty(), List.of(),
                model("model-harl"), "inside the chapel", "Chapel", List.of(
                        evidence(com.dndmaster.adventure.application.runtime.RuntimeEvidenceType.STORYBOOK),
                        evidence(com.dndmaster.adventure.application.runtime.RuntimeEvidenceType.RULEBOOK))));
        assertEquals(1, calls.get());
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

    private static com.dndmaster.adventure.application.runtime.RuntimeEvidence evidence(
            com.dndmaster.adventure.application.runtime.RuntimeEvidenceType type) {
        return new com.dndmaster.adventure.application.runtime.RuntimeEvidence(type,
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()),
                1, "page:1", "excerpt");
    }

    private static ScenarioModel model(String elementId) {
        return new ScenarioModel(1,
                List.of(new ScenarioModelElement(elementId, "actor", Map.of("name", "Harl"), List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "The adventure begins.");
    }
}
