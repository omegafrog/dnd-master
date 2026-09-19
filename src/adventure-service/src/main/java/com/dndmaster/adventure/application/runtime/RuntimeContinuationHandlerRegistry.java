package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Dispatches each supported movement result to its typed Runtime transition. */
public final class RuntimeContinuationHandlerRegistry implements RuntimeContinuationPort {
    private final Map<MovementFollowUpCommand.Kind, RuntimeContinuationHandler> handlers;

    public RuntimeContinuationHandlerRegistry(Map<MovementFollowUpCommand.Kind, RuntimeContinuationHandler> handlers) {
        EnumMap<MovementFollowUpCommand.Kind, RuntimeContinuationHandler> copy =
                new EnumMap<>(MovementFollowUpCommand.Kind.class);
        copy.putAll(Objects.requireNonNull(handlers, "continuation handlers must not be null"));
        this.handlers = Map.copyOf(copy);
    }

    @Override
    public RuntimeContinuationOutcome execute(RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation continuation) {
        RuntimeContinuationHandler handler = handlers.get(continuation.kind());
        if (handler == null) return RuntimeContinuationOutcome.retry("unsupported movement continuation kind");
        return Objects.requireNonNull(handler.handle(command, continuation),
                "continuation handler outcome must not be null");
    }

    public static RuntimeContinuationHandlerRegistry standard() {
        EnumMap<MovementFollowUpCommand.Kind, RuntimeContinuationHandler> handlers =
                new EnumMap<>(MovementFollowUpCommand.Kind.class);
        for (MovementFollowUpCommand.Kind kind : new MovementFollowUpCommand.Kind[] {
                MovementFollowUpCommand.Kind.COMBAT,
                MovementFollowUpCommand.Kind.WARNING,
                MovementFollowUpCommand.Kind.DIALOGUE,
                MovementFollowUpCommand.Kind.CHASE }) {
            handlers.put(kind, (command, continuation) -> RuntimeContinuationOutcome.applied(
                    kind.name() + ":transitioned:" + continuation.operationId()));
        }
        return new RuntimeContinuationHandlerRegistry(handlers);
    }
}
