package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Typed anti-corruption boundary for one authoritative Combat Map move. */
public record CombatMapMoveCommand(CombatActionCommand action, int distance, long expectedVersion) {
    public CombatMapMoveCommand {
        Objects.requireNonNull(action, "map movement command must not be null");
        if (action.combatMapId() == null) throw new IllegalArgumentException("mapped movement requires map id");
        if (distance <= 0) throw new IllegalArgumentException("movement distance must be positive");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
    }

    public CombatMapMoveCommand(CombatActionCommand action, int distance) {
        this(action, distance, action.mapVersion() == null ? action.expectedVersion() : action.mapVersion());
    }
}
