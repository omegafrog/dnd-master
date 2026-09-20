package com.dndmaster.adventure.application.combat;

public record CombatMapPreviewPosition(int x, int y) {
    public CombatMapPreviewPosition {
        if (x < 0 || y < 0) throw new IllegalArgumentException("movement positions must be non-negative");
    }
}
