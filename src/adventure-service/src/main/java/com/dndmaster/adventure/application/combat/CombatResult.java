package com.dndmaster.adventure.application.combat;

import java.util.UUID;

public record CombatResult(UUID operationId, CombatActorRole role, int diceTotal, String judgment,
        String resolutionStatus, boolean outcomeApplied) {
    public CombatResult(UUID operationId, CombatActorRole role, int diceTotal, String judgment) {
        this(operationId, role, diceTotal, judgment, "RESOLVED", true);
    }

    public CombatResult {
        if (judgment == null || judgment.isBlank()) throw new IllegalArgumentException("judgment must not be blank");
        judgment = judgment.trim();
        if (resolutionStatus == null || resolutionStatus.isBlank()) {
            throw new IllegalArgumentException("resolutionStatus must not be blank");
        }
        resolutionStatus = resolutionStatus.trim();
    }
}
