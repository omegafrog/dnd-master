package com.dndmaster.adventure.dice.domain;

import java.util.Objects;
import java.util.UUID;

public record RuleSetId(UUID value) {
    public RuleSetId { Objects.requireNonNull(value, "rule set id must not be null"); }
}
