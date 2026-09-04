package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.Objects;

/** The lookup agent receives only the locked, read-only ScenarioModel. */
public record ScenarioModelLookupRequest(String query, ScenarioModel lockedScenarioModel) {
    public ScenarioModelLookupRequest {
        query = required(query, "query");
        lockedScenarioModel = Objects.requireNonNull(lockedScenarioModel, "locked scenario model must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
