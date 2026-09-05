package com.dndmaster.adventure.application.combat;

/** Marker for an adapter failure that may be retried without changing combat intent. */
public final class CombatTransientFailureException extends RuntimeException {
    public CombatTransientFailureException(String message) { super(message); }
    public CombatTransientFailureException(String message, Throwable cause) { super(message, cause); }
}
