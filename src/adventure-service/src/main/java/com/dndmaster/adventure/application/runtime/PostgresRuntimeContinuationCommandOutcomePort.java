package com.dndmaster.adventure.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;

/** Durable Runtime continuation command store backed by the existing command repository. */
public final class PostgresRuntimeContinuationCommandOutcomePort implements RuntimeContinuationCommandOutcomePort {
    private final RuntimeTurnCommandRepository commands;
    private final ObjectMapper objectMapper;

    public PostgresRuntimeContinuationCommandOutcomePort(RuntimeTurnCommandRepository commands, ObjectMapper objectMapper) {
        this.commands = Objects.requireNonNull(commands, "runtime command repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override public synchronized CombatContinuationCommand combat(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, CombatContinuationCommand.class,
                (id, turn, operation, hostile, trigger) -> new CombatContinuationCommand(id, turn, operation, hostile, trigger));
    }
    @Override public synchronized WarningContinuationCommand warning(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, WarningContinuationCommand.class,
                (id, turn, operation, hostile, trigger) -> new WarningContinuationCommand(id, turn, operation, hostile, trigger));
    }
    @Override public synchronized DialogueContinuationCommand dialogue(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, DialogueContinuationCommand.class,
                (id, turn, operation, hostile, trigger) -> new DialogueContinuationCommand(id, turn, operation, hostile, trigger));
    }
    @Override public synchronized ChaseContinuationCommand chase(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, ChaseContinuationCommand.class,
                (id, turn, operation, hostile, trigger) -> new ChaseContinuationCommand(id, turn, operation, hostile, trigger));
    }

    private <T extends RuntimeContinuationCommandOutcome> T persist(
            RuntimeContinuationCommandPort.ContinuationCommand request, Class<T> type, OutcomeFactory<T> factory) {
        RuntimeTurnCommand command = request.command();
        RuntimeTurnCommand existing = commands.findByCommandId(command.commandId()).orElse(null);
        if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE
                && !existing.outcomeJson().isBlank()) return read(existing.outcomeJson(), type);
        MovementFollowUpRuntimeConsumer.Continuation continuation = request.continuation();
        T outcome = factory.create(command.commandId(), command.turnId(), continuation.operationId(),
                continuation.hostileTokenId(), continuation.trigger());
        try {
            commands.save(command.done(objectMapper.writeValueAsString(outcome)));
            return outcome;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("typed continuation command serialization failed", failure);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException failure) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "durable continuation command outcome is corrupt", failure);
        }
    }

    @FunctionalInterface
    private interface OutcomeFactory<T> {
        T create(java.util.UUID commandId, java.util.UUID turnId, java.util.UUID operationId,
                java.util.UUID hostileTokenId, String trigger);
    }
}
