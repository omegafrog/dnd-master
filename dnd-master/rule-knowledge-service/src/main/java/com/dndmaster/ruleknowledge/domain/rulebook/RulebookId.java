package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;
import java.util.UUID;

public record RulebookId(UUID value) {
    public RulebookId {
        Objects.requireNonNull(value, "rulebook id must not be null");
    }

    public static RulebookId generate() {
        return new RulebookId(UUID.randomUUID());
    }
}
