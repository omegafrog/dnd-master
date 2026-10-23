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
    private static final String COMBAT_CONTINUATION_COMMAND_TYPE = "movement.continuation.combat";

    private final RuntimeTurnCommandRepository commands;
    private final ObjectMapper objectMapper;
    private final java.util.function.Function<RuntimeContinuationCommandPort.ContinuationCommand,
            CombatContinuationCommand> adjudicator;

    public PostgresRuntimeContinuationCommandOutcomePort(RuntimeTurnCommandRepository commands, ObjectMapper objectMapper) {
        this(commands, objectMapper, request -> new CombatContinuationCommand(request.command().commandId(),
                request.command().turnId(), request.continuation().operationId(), request.continuation().hostileTokenId(),
                request.continuation().trigger(), request.continuation().kind()));
    }

    public PostgresRuntimeContinuationCommandOutcomePort(RuntimeTurnCommandRepository commands, ObjectMapper objectMapper,
            java.util.function.Function<RuntimeContinuationCommandPort.ContinuationCommand, CombatContinuationCommand> adjudicator) {
        this.commands = Objects.requireNonNull(commands, "runtime command repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
        this.adjudicator = Objects.requireNonNull(adjudicator, "movement adjudicator must not be null");
    }

    @Override public synchronized CombatContinuationCommand combat(RuntimeContinuationCommandPort.ContinuationCommand request) {
        validateRequest(request);
        RuntimeTurnCommand command = request.command();
        RuntimeTurnCommand existing = commands.findByCommandId(command.commandId()).orElse(null);
        if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
            if (existing.outcomeJson().isBlank()) throw new CorruptRuntimeContinuationOutcomeException(
                    "done continuation command has no durable outcome", null);
            CombatContinuationCommand persisted = read(existing.outcomeJson(), CombatContinuationCommand.class);
            validateOutcomeMatches(persisted, command, request.continuation(), existing.outcomeJson());
            return persisted;
        }
        CombatContinuationCommand response = adjudicator.apply(request);
        validateResponseIdentity(response, command, request.continuation());
        try {
            String canonical = objectMapper.writeValueAsString(response);
            commands.save(command.done(canonical));
            return response;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("typed continuation outcome serialization failed", failure);
        }
    }

    private void validateRequest(RuntimeContinuationCommandPort.ContinuationCommand request) {
        if (request == null || request.command() == null || request.continuation() == null) {
            throw new CorruptRuntimeContinuationOutcomeException("typed continuation request is required", null);
        }
        RuntimeTurnCommand command = request.command();
        MovementFollowUpRuntimeConsumer.Continuation continuation = request.continuation();
        if (continuation.kind() != MovementFollowUpCommand.Kind.COMBAT
                || !java.util.Set.of("HOSTILE_OBSERVED", "SPATIAL_FEATURE_REQUIRES_DECISION").contains(continuation.trigger())
                || ("HOSTILE_OBSERVED".equals(continuation.trigger()) != (continuation.hostileTokenId() != null))) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "typed continuation request kind or trigger does not match combat outcome", null);
        }
        if (command.commandId() == null || command.turnId() == null
                || continuation.operationId() == null
                || !command.turnId().equals(continuation.turnId())) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "typed continuation request identity does not match command", null);
        }
        if (!COMBAT_CONTINUATION_COMMAND_TYPE.equals(command.commandType())) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "typed continuation request command type does not match combat outcome", null);
        }
        validateCommandPayload(command, continuation);
    }

    private void validateOutcomeMatches(CombatContinuationCommand persisted, RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation continuation, String rawJson) {
        validateResponseIdentity(persisted, command, continuation);
        try {
            if (!objectMapper.writeValueAsString(persisted).equals(rawJson)) {
                throw new CorruptRuntimeContinuationOutcomeException("durable adjudication response is not canonical", null);
            }
        } catch (JsonProcessingException failure) {
            throw new CorruptRuntimeContinuationOutcomeException("durable adjudication response is corrupt", failure);
        }
    }

    private static void validateResponseIdentity(CombatContinuationCommand response, RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation continuation) {
        if (response == null || !command.commandId().equals(response.commandId())
                || !continuation.turnId().equals(response.turnId())
                || !continuation.operationId().equals(response.operationId())
                || !Objects.equals(continuation.hostileTokenId(), response.hostileTokenId())
                || !continuation.trigger().equals(response.trigger()) || continuation.kind() != response.kind()) {
            throw new CorruptRuntimeContinuationOutcomeException("GM response identity does not match stored movement operation", null);
        }
    }

    private void validateCommandPayload(RuntimeTurnCommand command,
            MovementFollowUpRuntimeConsumer.Continuation expected) {
        MovementFollowUpRuntimeConsumer.Continuation persisted = read(command.payloadJson(),
                MovementFollowUpRuntimeConsumer.Continuation.class);
        try {
            String canonical = objectMapper.writeValueAsString(persisted);
            if (!canonical.equals(command.payloadJson()) || !persisted.equals(expected)) {
                throw new CorruptRuntimeContinuationOutcomeException(
                        "typed continuation command payload does not match request", null);
            }
        } catch (JsonProcessingException failure) {
            throw new CorruptRuntimeContinuationOutcomeException(
                    "typed continuation command payload is corrupt", failure);
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

}
