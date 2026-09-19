package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Adapts one explicitly typed continuation port; it never delegates to GM tools. */
public final class TypedRuntimeContinuationCommandAdapter implements RuntimeTurnCommandAdapter {
    public enum Kind { COMBAT, WARNING, DIALOGUE, CHASE }

    private final Kind kind;
    private final RuntimeContinuationCommandPort port;

    public TypedRuntimeContinuationCommandAdapter(Kind kind, RuntimeContinuationCommandPort port) {
        this.kind = Objects.requireNonNull(kind);
        this.port = Objects.requireNonNull(port);
    }

    @Override
    public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
        String expected = "movement.continuation." + kind.name().toLowerCase(java.util.Locale.ROOT);
        if (!expected.equals(command.commandType())) return RuntimeTurnCommandExecution.permanentFailure("unexpected continuation command type");
        MovementFollowUpRuntimeConsumer.Continuation continuation = new MovementFollowUpRuntimeConsumer.Continuation(
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.valueOf(kind.name()),
                kind.name(), command.commandId());
        RuntimeContinuationCommandPort.ContinuationCommand typed =
                new RuntimeContinuationCommandPort.ContinuationCommand(command, continuation);
        return switch (kind) {
            case COMBAT -> port.combat(typed);
            case WARNING -> port.warning(typed);
            case DIALOGUE -> port.dialogue(typed);
            case CHASE -> port.chase(typed);
        };
    }
}
