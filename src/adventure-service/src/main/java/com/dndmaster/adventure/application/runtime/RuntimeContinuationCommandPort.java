package com.dndmaster.adventure.application.runtime;

/** Typed owning-context port for movement continuations. */
public interface RuntimeContinuationCommandPort {
    RuntimeTurnCommandExecution combat(ContinuationCommand command);

    record ContinuationCommand(RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation continuation) { }
}
