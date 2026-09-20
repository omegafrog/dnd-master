package com.dndmaster.adventure.application.runtime;

@FunctionalInterface
public interface RuntimeContinuationPort {
    RuntimeContinuationOutcome execute(RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation continuation);
}
