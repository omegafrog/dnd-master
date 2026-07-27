package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

public record ScenarioId(UUID value) {
    public ScenarioId { Objects.requireNonNull(value, "scenario id must not be null"); }
}
