package com.dndmaster.combatmap.application.movement;

/** The map changed after reservation and before the atomic final commit. */
public final class MovementFinalCommitConflictException extends IllegalStateException {
    public MovementFinalCommitConflictException() {
        super("combat map version changed before movement final commit");
    }
}
