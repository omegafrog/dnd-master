package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;

public record RegisteredRulebookReference(RulebookId rulebookId, OwnerPlayerId ownerPlayerId) {
    public RegisteredRulebookReference {
        Objects.requireNonNull(rulebookId, "rulebook id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
    }
}
