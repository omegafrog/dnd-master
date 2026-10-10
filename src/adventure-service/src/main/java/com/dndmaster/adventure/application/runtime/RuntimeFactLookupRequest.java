package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.List;
import java.util.Objects;

/** Snapshot supplied by Adventure Runtime to the composite lookup. */
public record RuntimeFactLookupRequest(
        String query,
        GameState gameState,
        List<RuntimeAddedFact> runtimeAddedFacts,
        ScenarioModel lockedScenarioModel,
        String currentSituation,
        String currentScene,
        List<RuntimeEvidence> evidence,
        String preparedSituation,
        List<LongTermAdventureFact> longTermFacts,
        List<String> knownEvidenceIds) {
    public RuntimeFactLookupRequest(String query, GameState gameState, List<RuntimeAddedFact> runtimeAddedFacts,
            ScenarioModel lockedScenarioModel) {
        this(query, gameState, runtimeAddedFacts, lockedScenarioModel, "", "", List.of(), "", List.of(), List.of());
    }

    public RuntimeFactLookupRequest(String query, GameState gameState, List<RuntimeAddedFact> runtimeAddedFacts,
            ScenarioModel lockedScenarioModel, String currentSituation, String currentScene, List<RuntimeEvidence> evidence) {
        this(query, gameState, runtimeAddedFacts, lockedScenarioModel, currentSituation, currentScene, evidence, "", List.of(), List.of());
    }

    public RuntimeFactLookupRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        query = query.trim();
        gameState = Objects.requireNonNull(gameState, "game state must not be null");
        runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
        if (runtimeAddedFacts.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("runtime facts must not contain null");
        lockedScenarioModel = Objects.requireNonNull(lockedScenarioModel, "locked scenario model must not be null");
        currentSituation = currentSituation == null ? "" : currentSituation.trim();
        currentScene = currentScene == null ? "" : currentScene.trim();
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        if (evidence.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("evidence must not contain null");
        preparedSituation = preparedSituation == null ? "" : preparedSituation.trim();
        longTermFacts = List.copyOf(Objects.requireNonNull(longTermFacts, "long-term facts must not be null"));
        knownEvidenceIds = List.copyOf(Objects.requireNonNull(knownEvidenceIds, "known evidence ids must not be null"));
        if (knownEvidenceIds.stream().anyMatch(id -> id == null || !id.startsWith("IMMUTABLE_DOCUMENT:")))
            throw new IllegalArgumentException("known evidence ids must use immutable document references");
    }
}
