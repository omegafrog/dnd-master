package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;

public record RegisteredRulebookReference(RulebookId rulebookId) {
    public RegisteredRulebookReference {
        Objects.requireNonNull(rulebookId, "rulebook id must not be null");
    }
}
