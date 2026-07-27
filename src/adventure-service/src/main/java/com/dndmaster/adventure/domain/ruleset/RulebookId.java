package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;
import java.util.UUID;

public record RulebookId(UUID value) {
    public RulebookId {
        Objects.requireNonNull(value, "rulebook id must not be null");
    }
}
