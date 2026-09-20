package com.dndmaster.combatmap.application.movement;

/** Public result state, separate from the durable operation lifecycle. */
public enum MovementResolutionOutcomeStatus {
    COMMITTED,
    INTERRUPTED,
    CHECK_REQUIRED,
    RETRY_REQUIRED,
    CANCELLED
}
