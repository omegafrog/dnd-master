package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL-backed continuation state through the existing runtime command saga. */
public final class RuntimeCommandContinuationStatePort implements RuntimeContinuationStatePort {
    private final RuntimeTurnCommandRepository commands;
    private final ObjectMapper objectMapper;

    public RuntimeCommandContinuationStatePort(RuntimeTurnCommandRepository commands, ObjectMapper objectMapper) {
        this.commands = Objects.requireNonNull(commands, "runtime command repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public synchronized RuntimeContinuationState apply(RuntimeContinuationCommandPort.ContinuationCommand request) {
        RuntimeTurnCommand command = request.command();
        RuntimeTurnCommand existing = commands.findByCommandId(command.commandId()).orElse(null);
        if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE
                && !existing.outcomeJson().isBlank()) {
            return read(existing.outcomeJson());
        }
        RuntimeContinuationState state = new RuntimeContinuationState(command.commandId(), command.turnId(),
                request.continuation().operationId(), request.continuation().hostileTokenId(),
                request.continuation().kind(), RuntimeContinuationState.Status.APPLIED);
        try {
            commands.save(command.done(objectMapper.writeValueAsString(state)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("continuation state serialization failed", failure);
        }
        return state;
    }

    public RuntimeContinuationState find(UUID commandId) {
        return commands.findByCommandId(commandId).filter(command -> !command.outcomeJson().isBlank())
                .map(command -> read(command.outcomeJson())).orElse(null);
    }

    private RuntimeContinuationState read(String json) {
        try {
            return objectMapper.readValue(json, RuntimeContinuationState.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("durable continuation state is malformed", failure);
        }
    }
}
