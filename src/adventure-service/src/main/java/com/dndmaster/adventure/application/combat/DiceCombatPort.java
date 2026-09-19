package com.dndmaster.adventure.application.combat;

public interface DiceCombatPort {
    int roll(CombatActionCommand command);

    default int rollSpatialCheck(SpatialCheckRollCommand command) {
        throw new UnsupportedOperationException("spatial check dice roll is unavailable");
    }
}
