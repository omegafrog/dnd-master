package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** Isolated read-only lookup boundary for one player action and its scoped facts and evidence. */
@FunctionalInterface
public interface ScenarioModelLookupAgentPort {
    ScenarioLookupResult lookup(ScenarioModelLookupRequest request);

    default ScenarioLookupResult lookup(UUID soloPlayerId, ScenarioModelLookupRequest request) {
        return lookup(request);
    }
}
