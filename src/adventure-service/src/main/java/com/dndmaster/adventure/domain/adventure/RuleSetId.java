package com.dndmaster.adventure.domain.adventure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public record RuleSetId(UUID value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public RuleSetId { Objects.requireNonNull(value, "rule set id must not be null"); }

    @JsonValue
    public UUID jsonValue() { return value; }
}
