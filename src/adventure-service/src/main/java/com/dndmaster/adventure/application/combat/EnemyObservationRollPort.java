package com.dndmaster.adventure.application.combat;

/** Internal enemy-owned roll boundary; it is not the player roll contract. */
@FunctionalInterface
public interface EnemyObservationRollPort {
    int rollEnemyObservation(EnemyObservationRollCommand command);
}
