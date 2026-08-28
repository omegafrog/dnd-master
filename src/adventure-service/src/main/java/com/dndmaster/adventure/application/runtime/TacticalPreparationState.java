package com.dndmaster.adventure.application.runtime;

/** Player-visible state composed from plan intent, the durable job, and its scene snapshot. */
public enum TacticalPreparationState {
    NOT_REQUIRED,
    REQUIRED_PENDING,
    PREPARING,
    READY,
    FAILED_RETRYABLE
}
