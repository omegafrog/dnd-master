package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory adapter used until the owning Runtime state repository is configured. */
public final class InMemoryRuntimeContinuationStatePort implements RuntimeContinuationStatePort {
    private final Map<UUID, RuntimeContinuationState> states = new ConcurrentHashMap<>();

    @Override
    public RuntimeContinuationState apply(RuntimeContinuationCommandPort.ContinuationCommand command) {
        RuntimeTurnCommand source = command.command();
        return states.computeIfAbsent(source.commandId(), ignored -> new RuntimeContinuationState(
                source.commandId(), source.turnId(), command.continuation().operationId(),
                command.continuation().kind(), RuntimeContinuationState.Status.APPLIED));
    }

    public RuntimeContinuationState find(UUID commandId) {
        return states.get(commandId);
    }
}
