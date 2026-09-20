package com.dndmaster.combatmap.application.movement;

/** The command identity already belongs to movement input with another fingerprint. */
public final class MovementCommandConflictException extends IllegalStateException {
    public MovementCommandConflictException() {
        super("movement command id reused with a different fingerprint");
    }
}
