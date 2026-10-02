package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;

/** Performs required cross-service checks before a runtime turn becomes durable. */
@FunctionalInterface
public interface RuntimeTurnCommitGate {
    void beforeCommit(Adventure pendingAdventure, RuntimeTurn turn);

    static RuntimeTurnCommitGate none() { return (adventure, turn) -> {}; }
}
