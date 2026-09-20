package com.dndmaster.adventure.application.runtime;

@FunctionalInterface
public interface RuntimeContinuationHandler {
    RuntimeContinuationOutcome handle(RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation continuation);
}
