package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** Typed, read-only ScenarioModel lookup boundary for the AI service. */
@FunctionalInterface
public interface ScenarioModelLookupAgentPort {
    ScenarioLookupResult lookup(ScenarioModelLookupRequest request);

    default ScenarioLookupResult lookup(UUID soloPlayerId, ScenarioModelLookupRequest request) {
        return lookup(request);
    }
}
