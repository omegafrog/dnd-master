package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;
import java.util.UUID;

public record RuleSetId(UUID value) {
    public RuleSetId {
        Objects.requireNonNull(value, "rule set id must not be null");
    }

    public static RuleSetId generate() {
        return new RuleSetId(UUID.randomUUID());
    }
}
