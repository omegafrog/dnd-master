package com.dndmaster.adventure.application.combat;

/** Safe, typed result for a required spatial preparation failure. */
public final class CombatMapPreparationBlockedException extends RuntimeException {
    public CombatMapPreparationBlockedException() {
        super("combat map preparation is blocked");
    }
}
