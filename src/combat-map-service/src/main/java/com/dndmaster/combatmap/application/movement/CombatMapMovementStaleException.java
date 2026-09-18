package com.dndmaster.combatmap.application.movement;

public final class CombatMapMovementStaleException extends IllegalStateException {
    public CombatMapMovementStaleException() {
        super("combat map version does not match");
    }
}
