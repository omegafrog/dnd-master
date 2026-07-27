package com.dndmaster.adventure.application.combat;

import java.util.UUID;

public record CombatResult(UUID operationId, CombatActorRole role, int diceTotal, String judgment) {
    public CombatResult {
        if (judgment == null || judgment.isBlank()) throw new IllegalArgumentException("judgment must not be blank");
        judgment = judgment.trim();
    }
}
