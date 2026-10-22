package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;

/** Performs required cross-service checks before a runtime turn becomes durable. */
@FunctionalInterface
public interface RuntimeTurnCommitGate {
    void beforeCommit(Adventure pendingAdventure, RuntimeTurn turn);

    default void beforeCommit(Adventure pendingAdventure, RuntimeTurn turn, CurrentSituation previousSituation) {
        beforeCommit(pendingAdventure, turn);
    }

    static RuntimeTurnCommitGate none() { return (adventure, turn) -> {}; }
}
