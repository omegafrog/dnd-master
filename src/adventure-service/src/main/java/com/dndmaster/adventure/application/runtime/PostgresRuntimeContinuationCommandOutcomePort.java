package com.dndmaster.adventure.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
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
                (id, turn, operation, hostile, trigger, kind) -> new CombatContinuationCommand(id, turn, operation, hostile, trigger, kind));
    }
    @Override public synchronized WarningContinuationCommand warning(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, WarningContinuationCommand.class,
                (id, turn, operation, hostile, trigger, kind) -> new WarningContinuationCommand(id, turn, operation, hostile, trigger, kind));
    }
    @Override public synchronized DialogueContinuationCommand dialogue(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, DialogueContinuationCommand.class,
                (id, turn, operation, hostile, trigger, kind) -> new DialogueContinuationCommand(id, turn, operation, hostile, trigger, kind));
    }
    @Override public synchronized ChaseContinuationCommand chase(RuntimeContinuationCommandPort.ContinuationCommand request) {
        return persist(request, ChaseContinuationCommand.class,
                (id, turn, operation, hostile, trigger, kind) -> new ChaseContinuationCommand(id, turn, operation, hostile, trigger, kind));
    }

    private <T extends RuntimeContinuationCommandOutcome> T persist(
            RuntimeContinuationCommandPort.ContinuationCommand request, Class<T> type, OutcomeFactory<T> factory) {
        RuntimeTurnCommand command = request.command();
        RuntimeTurnCommand existing = commands.findByCommandId(command.commandId()).orElse(null);
        MovementFollowUpRuntimeConsumer.Continuation continuation = request.continuation();
        T expected = factory.create(command.commandId(), command.turnId(), continuation.operationId(),
                continuation.hostileTokenId(), continuation.trigger(), continuation.kind());
        if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
            if (existing.outcomeJson().isBlank()) {
                throw new CorruptRuntimeContinuationOutcomeException(
                        "done continuation command has no durable outcome", null);
            }
            T persisted = read(existing.outcomeJson(), type);
            validateMatches(persisted, expected, existing.outcomeJson());
            return persisted;
        }
        try {
            String canonical = objectMapper.writeValueAsString(expected);
            commands.save(command.done(canonical));
            return expected;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("typed continuation command serialization failed", failure);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try (JsonParser parser = objectMapper.createParser(json)) {
            T value = objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readValue(parser);
            if (parser.nextToken() != null) throw new IOException("trailing JSON");
            return value;
        } catch (IOException failure) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "durable continuation command outcome is corrupt", failure);
        }
    }

    private void validateMatches(RuntimeContinuationCommandOutcome persisted,
            RuntimeContinuationCommandOutcome expected, String rawJson) {
        try {
            String canonical = objectMapper.writeValueAsString(persisted);
            if (!canonical.equals(rawJson) || !persisted.equals(expected)) {
                throw new CorruptRuntimeContinuationOutcomeException(
                        "durable continuation command outcome does not match request", null);
            }
        } catch (JsonProcessingException failure) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "durable continuation command outcome is corrupt", failure);
        }
    }

    @FunctionalInterface
    private interface OutcomeFactory<T> {
        T create(java.util.UUID commandId, java.util.UUID turnId, java.util.UUID operationId,
                java.util.UUID hostileTokenId, String trigger, MovementFollowUpCommand.Kind kind);
    }
}
