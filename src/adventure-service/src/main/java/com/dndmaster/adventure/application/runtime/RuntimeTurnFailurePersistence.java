package com.dndmaster.adventure.application.runtime;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists presentation failures outside the submit transaction so retry state survives rollback. */
public class RuntimeTurnFailurePersistence {
    private final RuntimeTurnRepository turns;
    private final RuntimeTurnFailureRepository failures;

    public RuntimeTurnFailurePersistence(RuntimeTurnRepository turns) {
        this(turns, new NoopRuntimeTurnFailureRepository());
    }

    public RuntimeTurnFailurePersistence(RuntimeTurnRepository turns, RuntimeTurnFailureRepository failures) {
        this.turns = java.util.Objects.requireNonNull(turns, "runtime turn repository must not be null");
        this.failures = java.util.Objects.requireNonNull(failures, "failure repository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(RuntimeTurn failedTurn) {
        turns.save(failedTurn.markPresentationFailed());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(RuntimeTurn failedTurn, RuntimeTurnFailureArtifact failure) {
        turns.save(failedTurn.markPresentationFailed());
        failures.append(failure);
    }
}
