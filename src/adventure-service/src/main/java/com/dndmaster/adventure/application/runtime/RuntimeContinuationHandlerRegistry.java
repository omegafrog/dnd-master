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

    public static RuntimeContinuationHandlerRegistry standard(RuntimeTurnCommandAdapter adapter) {
        Objects.requireNonNull(adapter, "runtime command adapter must not be null");
        EnumMap<MovementFollowUpCommand.Kind, RuntimeContinuationHandler> handlers =
                new EnumMap<>(MovementFollowUpCommand.Kind.class);
        for (MovementFollowUpCommand.Kind kind : new MovementFollowUpCommand.Kind[] {
                MovementFollowUpCommand.Kind.COMBAT,
                MovementFollowUpCommand.Kind.WARNING,
                MovementFollowUpCommand.Kind.DIALOGUE,
                MovementFollowUpCommand.Kind.CHASE }) {
            handlers.put(kind, (command, continuation) -> transition(adapter, command, kind));
        }
        return new RuntimeContinuationHandlerRegistry(handlers);
    }

    public static RuntimeContinuationHandlerRegistry standard(RuntimeTurnCommandAdapterRegistry registry) {
        EnumMap<MovementFollowUpCommand.Kind, RuntimeContinuationHandler> handlers =
                new EnumMap<>(MovementFollowUpCommand.Kind.class);
        for (MovementFollowUpCommand.Kind kind : new MovementFollowUpCommand.Kind[] {
                MovementFollowUpCommand.Kind.COMBAT, MovementFollowUpCommand.Kind.WARNING,
                MovementFollowUpCommand.Kind.DIALOGUE, MovementFollowUpCommand.Kind.CHASE }) {
            String type = "movement.continuation." + kind.name().toLowerCase(java.util.Locale.ROOT);
            RuntimeTurnCommandAdapter adapter = registry.registered(type);
            if (adapter == null) throw new IllegalStateException("missing registered continuation adapter: " + type);
            handlers.put(kind, (command, continuation) -> transition(adapter, command, kind));
        }
        return new RuntimeContinuationHandlerRegistry(handlers);
    }

    private static RuntimeContinuationOutcome transition(RuntimeTurnCommandAdapter adapter,
            RuntimeTurnCommand command, MovementFollowUpCommand.Kind kind) {
        String expectedType = "movement.continuation."
                + kind.name().toLowerCase(java.util.Locale.ROOT);
        if (!expectedType.equals(command.commandType())) {
            return RuntimeContinuationOutcome.retry("continuation command type does not match " + kind.name());
        }
        RuntimeTurnCommandExecution execution = Objects.requireNonNull(adapter.execute(command),
                "runtime continuation adapter result must not be null");
        return execution.status() == RuntimeTurnCommandExecution.Status.DONE
                ? RuntimeContinuationOutcome.applied(execution.value())
                : RuntimeContinuationOutcome.retry(execution.value());
    }
}
