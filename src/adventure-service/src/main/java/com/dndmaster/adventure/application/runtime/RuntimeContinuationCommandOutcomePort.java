package com.dndmaster.adventure.application.runtime;

/** Persists and returns the typed command that owns each Runtime continuation. */
public interface RuntimeContinuationCommandOutcomePort {
    CombatContinuationCommand combat(RuntimeContinuationCommandPort.ContinuationCommand command);
}
