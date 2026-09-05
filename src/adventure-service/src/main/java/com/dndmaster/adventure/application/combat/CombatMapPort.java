package com.dndmaster.adventure.application.combat;

public interface CombatMapPort {
    /** Legacy adapter entry point retained for runtime-turn compatibility. */
    void validateAndMove(CombatActionCommand command);

    /** Dedicated movement boundary carrying the map version and command identity. */
    default CombatMapMoveResult move(CombatMapMoveCommand command) {
        validateAndMove(command.action());
        return new CombatMapMoveResult(command.action().expectedVersion() + 1);
    }
}
