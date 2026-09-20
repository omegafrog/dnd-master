package com.dndmaster.combatmap.application.movement;

public final class CombatMapMovementPreviewMismatchException extends IllegalStateException {
    public CombatMapMovementPreviewMismatchException() {
        super("movement confirmation does not match the server preview");
    }
}
