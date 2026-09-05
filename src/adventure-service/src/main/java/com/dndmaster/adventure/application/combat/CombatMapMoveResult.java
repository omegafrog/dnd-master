package com.dndmaster.adventure.application.combat;

public record CombatMapMoveResult(long version) {
    public CombatMapMoveResult {
        if (version < 0) throw new IllegalArgumentException("map version must be non-negative");
    }
}
