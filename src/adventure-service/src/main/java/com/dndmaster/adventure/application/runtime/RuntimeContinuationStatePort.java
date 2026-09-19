package com.dndmaster.adventure.application.runtime;

/** Stores an idempotent, typed Runtime continuation transition. */
@FunctionalInterface
public interface RuntimeContinuationStatePort {
    RuntimeContinuationState apply(RuntimeContinuationCommandPort.ContinuationCommand command);
}
