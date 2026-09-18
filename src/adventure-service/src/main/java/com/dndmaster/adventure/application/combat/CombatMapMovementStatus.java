package com.dndmaster.adventure.application.combat;

/** Typed Adventure-side view of the Combat Map reservation lifecycle. */
public enum CombatMapMovementStatus {
    PREPARING,
    RETRY_WAIT,
    READY_TO_COMMIT,
    COMMITTED,
    CANCELLED
}
