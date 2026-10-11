package com.dndmaster.adventure.application.combat;

public interface DiceCombatPort {
    int roll(CombatActionCommand command);

    default int rollDamage(int diceCount, int dieSides, int modifier) {
        throw new UnsupportedOperationException("damage dice require a command-aware persistent roller");
    }

    default int rollDamage(CombatActionCommand command, int diceCount, int dieSides, int modifier) {
        return rollDamage(diceCount, dieSides, modifier);
    }

    default int rollSpatialCheck(SpatialCheckRollCommand command) {
        throw new UnsupportedOperationException("spatial check dice roll is unavailable");
    }
    default int rollEnemyObservation(EnemyObservationRollCommand command) {
        throw new UnsupportedOperationException("enemy observation dice roll is unavailable");
    }
}
