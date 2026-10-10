package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Minimal, conversation-free input for resolving facts relevant to one player action. */
public record ScenarioModelLookupRequest(String query, ScenarioModel lockedScenarioModel,
        String currentSituation, String currentScene, Map<String, Object> establishedGameState,
        List<RuntimeAddedFact> runtimeAddedFacts, List<RuntimeEvidence> evidence, String preparedSituation,
        List<LongTermAdventureFact> longTermFacts, List<String> knownEvidenceIds) {
    public ScenarioModelLookupRequest(String query, ScenarioModel lockedScenarioModel) {
        this(query, lockedScenarioModel, "", "", Map.of(), List.of(), List.of(), "", List.of(), List.of());
    }
    public ScenarioModelLookupRequest(String query, ScenarioModel lockedScenarioModel, String currentSituation,
            String currentScene, Map<String, Object> establishedGameState, List<RuntimeAddedFact> runtimeAddedFacts,
            List<RuntimeEvidence> evidence) {
        this(query, lockedScenarioModel, currentSituation, currentScene, establishedGameState, runtimeAddedFacts,
                evidence, "", List.of(), List.of());
    }

    public ScenarioModelLookupRequest {
        query = required(query, "query");
        lockedScenarioModel = Objects.requireNonNull(lockedScenarioModel, "locked scenario model must not be null");
        currentSituation = currentSituation == null ? "" : currentSituation.trim();
        currentScene = currentScene == null ? "" : currentScene.trim();
        establishedGameState = Map.copyOf(Objects.requireNonNull(establishedGameState, "game state must not be null"));
        runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        preparedSituation = preparedSituation == null ? "" : preparedSituation.trim();
        longTermFacts = List.copyOf(Objects.requireNonNull(longTermFacts, "long-term facts must not be null"));
        knownEvidenceIds = List.copyOf(Objects.requireNonNull(knownEvidenceIds, "known evidence ids must not be null"));
        if (knownEvidenceIds.stream().anyMatch(id -> id == null || !id.startsWith("IMMUTABLE_DOCUMENT:")))
            throw new IllegalArgumentException("known evidence ids must use immutable document references");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
