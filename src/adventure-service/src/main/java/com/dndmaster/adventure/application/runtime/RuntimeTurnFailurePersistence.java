package com.dndmaster.adventure.application.runtime;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists presentation failures outside the submit transaction so retry state survives rollback. */
public class RuntimeTurnFailurePersistence {
    private final RuntimeTurnRepository turns;

    public RuntimeTurnFailurePersistence(RuntimeTurnRepository turns) {
        this.turns = turns;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(RuntimeTurn failedTurn) {
        turns.save(failedTurn.markPresentationFailed());
    }
}
