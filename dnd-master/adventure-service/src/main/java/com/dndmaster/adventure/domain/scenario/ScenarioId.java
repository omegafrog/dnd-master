package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.UUID;

public record ScenarioId(UUID value) {
    public ScenarioId {
        Objects.requireNonNull(value, "scenario id must not be null");
    }

    public static ScenarioId generate() {
        return new ScenarioId(UUID.randomUUID());
    }
}
