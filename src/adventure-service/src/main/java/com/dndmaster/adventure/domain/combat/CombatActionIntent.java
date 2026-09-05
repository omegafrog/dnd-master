package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatActionIntent(UUID actorId, String action, TurnResourceCost cost) {
    public CombatActionIntent {
        Objects.requireNonNull(actorId, "actor id must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
        Objects.requireNonNull(cost, "cost must not be null");
    }
}
