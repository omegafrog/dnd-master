package com.dndmaster.adventure.application.combat;

public final class CombatExternalFailureException extends RuntimeException {
    public CombatExternalFailureException(Throwable cause) {
        super("combat external step failed", cause);
    }
}
