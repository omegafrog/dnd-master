package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.core.JsonParser;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Consumes the durable movement event and records its typed Runtime continuation. */
public final class MovementFollowUpRuntimeConsumer {
    private final SessionEventRepository events;
    private final RuntimeTurnCommandRepository commands;
    private final ObjectMapper objectMapper;
    private final RuntimeContinuationPort continuationPort;

    public MovementFollowUpRuntimeConsumer(SessionEventRepository events, RuntimeTurnCommandRepository commands,
            ObjectMapper objectMapper, RuntimeContinuationPort continuationPort) {
        this.events = Objects.requireNonNull(events);
        this.commands = Objects.requireNonNull(commands);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.continuationPort = Objects.requireNonNull(continuationPort);
    }

    public synchronized MovementFollowUpPort.Result consume(RuntimeTurnCommand source, MovementFollowUpCommand expected) {
        try {
            SessionEvent event = events.after(source.sessionId(), -1).stream()
                    .filter(candidate -> candidate.eventId().equals(expected.commandId()))
                    .findFirst().orElseThrow(() -> new PermanentFollowUpFailure("movement follow-up event is not durable"));
            if (!"MOVEMENT_FOLLOW_UP".equals(event.type())) {
                throw new PermanentFollowUpFailure("unexpected movement follow-up event type");
            }
            String canonicalPayload = objectMapper.writeValueAsString(expected);
            if (!canonicalPayload.equals(event.payload())) {
                throw new PermanentFollowUpFailure("movement follow-up event payload is not canonical");
            }
            var eventPayload = readStrictJson(event.payload());
            validateIdentity(eventPayload, "commandId");
            validateIdentity(eventPayload, "operationId");
            validateIdentity(eventPayload, "hostileTokenId");
            validateIdentity(eventPayload, "turnId");
            JsonNode expectedPayload = objectMapper.valueToTree(expected);
            if (!eventPayload.equals(expectedPayload)) {
                throw new PermanentFollowUpFailure("movement follow-up event payload mismatch");
            }
            MovementFollowUpCommand followUp = objectMapper.treeToValue(eventPayload, MovementFollowUpCommand.class);
            if (!source.turnId().equals(followUp.turnId())) {
                throw new PermanentFollowUpFailure("movement follow-up belongs to another turn");
            }
            MovementFollowUpCommand.Kind kind = requiredKind(eventPayload);
            if (kind != expected.kind()) {
                throw new PermanentFollowUpFailure("movement follow-up kind does not match selected typed result");
            }
            if (kind == MovementFollowUpCommand.Kind.CONTINUATION) {
                throw new PermanentFollowUpFailure("movement follow-up kind is not supported");
            }
            UUID continuationId = UUID.nameUUIDFromBytes(
                    ("movement-continuation:" + followUp.commandId()).getBytes(StandardCharsets.UTF_8));
            RuntimeTurnCommand existing = commands.findByCommandId(continuationId).orElse(null);
            if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
                return MovementFollowUpPort.Result.done(existing.outcomeJson());
            }
            int order = commands.findByTurnId(source.turnId()).stream()
                    .mapToInt(RuntimeTurnCommand::executionOrder).max().orElse(source.executionOrder()) + 1;
            String payload = objectMapper.writeValueAsString(new Continuation(kind, followUp.trigger(), followUp.operationId(),
                    source.turnId(), followUp.hostileTokenId()));
            RuntimeTurnCommand continuation = existing == null
                    ? RuntimeTurnCommand.create(source.turnId(), continuationId, source.adventureId(), source.sessionId(),
                            source.ownerPlayerId(), source.targetContext(),
                            "movement.continuation." + kind.name().toLowerCase(java.util.Locale.ROOT), payload, order)
                    : existing;
            if (existing == null) commands.save(continuation);
            RuntimeContinuationOutcome outcome;
            try {
                outcome = continuationPort.execute(continuation,
                        new Continuation(kind, followUp.trigger(), followUp.operationId(), source.turnId(), followUp.hostileTokenId()));
            } catch (PermanentFollowUpFailure failure) {
                commands.save(continuation.failed(failure.getMessage(), failure.getMessage()));
                return MovementFollowUpPort.Result.permanentFailure(failure.getMessage());
            } catch (RuntimeException failure) {
                commands.save(continuation.failed(failure.getMessage(), failure.getMessage()));
                return MovementFollowUpPort.Result.retry(failure.getMessage());
            }
            if (outcome.status() == RuntimeContinuationOutcome.Status.RETRY) {
                commands.save(continuation.failed(outcome.value(), outcome.value()));
                return MovementFollowUpPort.Result.retry(outcome.value());
            }
            if (outcome.status() == RuntimeContinuationOutcome.Status.PERMANENT_FAILURE) {
                commands.save(continuation.failed(outcome.value(), outcome.value()));
                return MovementFollowUpPort.Result.permanentFailure(outcome.value());
            }
            commands.save(continuation.done(outcome.value()));
            return MovementFollowUpPort.Result.done(outcome.value());
        } catch (PermanentFollowUpFailure failure) {
            return MovementFollowUpPort.Result.permanentFailure(failure.getMessage());
        } catch (JsonProcessingException failure) {
            return MovementFollowUpPort.Result.permanentFailure(
                    "invalid durable movement follow-up payload: " + failure.getOriginalMessage());
        } catch (IOException failure) {
            return MovementFollowUpPort.Result.permanentFailure(
                    "invalid durable movement follow-up payload: " + failure.getMessage());
        } catch (IllegalArgumentException failure) {
            return MovementFollowUpPort.Result.permanentFailure(failure.getMessage());
        } catch (RuntimeException failure) {
            return MovementFollowUpPort.Result.retry(failure.getMessage());
        }
    }

    private JsonNode readStrictJson(String payload) throws IOException {
        try (JsonParser parser = objectMapper.createParser(payload)) {
            JsonNode value = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readTree(parser);
            if (value == null || !value.isObject()) {
                throw new PermanentFollowUpFailure("movement follow-up event payload must be an object");
            }
            if (parser.nextToken() != null) {
                throw new PermanentFollowUpFailure("invalid durable movement follow-up payload: trailing JSON");
            }
            return value;
        }
    }

    private static void validateIdentity(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        if (payload == null || !payload.isObject() || !payload.hasNonNull(field)
                || !payload.path(field).isTextual() || payload.path(field).asText().isBlank()) {
            throw new PermanentFollowUpFailure("movement follow-up field " + field + " is required");
        }
        try {
            UUID.fromString(payload.path(field).asText());
        } catch (IllegalArgumentException failure) {
            throw new PermanentFollowUpFailure("movement follow-up field " + field + " is invalid");
        }
    }

    private static MovementFollowUpCommand.Kind requiredKind(JsonNode payload) {
        if (payload == null || !payload.hasNonNull("kind") || !payload.path("kind").isTextual()
                || payload.path("kind").asText().isBlank()) {
            throw new PermanentFollowUpFailure("movement follow-up field kind is required");
        }
        try {
            return MovementFollowUpCommand.Kind.valueOf(payload.path("kind").asText());
        } catch (IllegalArgumentException failure) {
            throw new PermanentFollowUpFailure("movement follow-up field kind is invalid");
        }
    }

    public record Continuation(MovementFollowUpCommand.Kind kind, String trigger, UUID operationId, UUID turnId,
            UUID hostileTokenId) {
        public Continuation(MovementFollowUpCommand.Kind kind, String trigger, UUID operationId, UUID turnId) {
            this(kind, trigger, operationId, turnId, null);
        }
    }
}
