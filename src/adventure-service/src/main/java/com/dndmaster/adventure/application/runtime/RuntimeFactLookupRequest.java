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
        ScenarioModel lockedScenarioModel) {
    public RuntimeFactLookupRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        query = query.trim();
        gameState = Objects.requireNonNull(gameState, "game state must not be null");
        runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
        if (runtimeAddedFacts.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("runtime facts must not contain null");
        lockedScenarioModel = Objects.requireNonNull(lockedScenarioModel, "locked scenario model must not be null");
    }
}
