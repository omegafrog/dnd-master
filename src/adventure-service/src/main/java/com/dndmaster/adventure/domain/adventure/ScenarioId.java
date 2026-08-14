package com.dndmaster.adventure.domain.adventure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public record ScenarioId(UUID value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ScenarioId { Objects.requireNonNull(value, "scenario id must not be null"); }

    @JsonValue
    public UUID jsonValue() { return value; }
}
