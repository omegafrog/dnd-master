package com.dndmaster.adventure.application.combat;

/** The confirmed combat record could not be added to the Adventure conversation. */
public final class CombatNarrationPersistenceException extends RuntimeException {
    public CombatNarrationPersistenceException(Throwable cause) {
        super("confirmed combat record could not be saved", cause);
    }
}
