package com.dndmaster.adventure.application.combat;

public interface DiceCombatPort {
    int roll(CombatActionCommand command);

    default int rollDamage(int diceCount, int dieSides, int modifier) {
        if (diceCount < 1 || dieSides < 2) throw new IllegalArgumentException("damage dice are invalid");
        int total = modifier;
        for (int index = 0; index < diceCount; index++) {
            total += java.util.concurrent.ThreadLocalRandom.current().nextInt(1, dieSides + 1);
        }
        return total;
    }

    default int rollSpatialCheck(SpatialCheckRollCommand command) {
        throw new UnsupportedOperationException("spatial check dice roll is unavailable");
    }
    default int rollEnemyObservation(EnemyObservationRollCommand command) {
        throw new UnsupportedOperationException("enemy observation dice roll is unavailable");
    }
}
