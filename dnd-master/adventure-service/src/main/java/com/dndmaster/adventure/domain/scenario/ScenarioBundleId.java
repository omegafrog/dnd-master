package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.UUID;

public record ScenarioBundleId(UUID value) {
    public ScenarioBundleId {
        value = Objects.requireNonNull(value, "scenario bundle id must not be null");
    }

    public static ScenarioBundleId generate() {
        return new ScenarioBundleId(UUID.randomUUID());
    }
}
