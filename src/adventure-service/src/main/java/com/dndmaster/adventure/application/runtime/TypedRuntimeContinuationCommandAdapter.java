package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;

/** Adapts one explicitly typed continuation port; it never delegates to GM tools. */
public final class TypedRuntimeContinuationCommandAdapter implements RuntimeTurnCommandAdapter {
    public enum Kind { COMBAT, WARNING, DIALOGUE, CHASE }

    private final Kind kind;
    private final RuntimeContinuationCommandPort port;
    private final ObjectMapper objectMapper;

    public TypedRuntimeContinuationCommandAdapter(Kind kind, RuntimeContinuationCommandPort port) {
        this.kind = Objects.requireNonNull(kind);
        this.port = Objects.requireNonNull(port);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
        if (command == null || command.commandId() == null || command.turnId() == null) {
            return RuntimeTurnCommandExecution.permanentFailure("continuation command identity is required");
        }
        String expected = "movement.continuation." + kind.name().toLowerCase(java.util.Locale.ROOT);
        if (!expected.equals(command.commandType())) return RuntimeTurnCommandExecution.permanentFailure("unexpected continuation command type");
        final RuntimeContinuationCommandPort.ContinuationCommand typed;
        try {
            typed = decode(command);
        } catch (PermanentFollowUpFailure | java.io.IOException | IllegalArgumentException failure) {
            return RuntimeTurnCommandExecution.permanentFailure(failure.getMessage());
        }
        try {
            return switch (kind) {
                case COMBAT -> port.combat(typed);
                case WARNING -> port.warning(typed);
                case DIALOGUE -> port.dialogue(typed);
                case CHASE -> port.chase(typed);
            };
        } catch (PermanentFollowUpFailure failure) {
            return RuntimeTurnCommandExecution.permanentFailure(failure.getMessage());
        } catch (RuntimeException failure) {
            return RuntimeTurnCommandExecution.transientFailure(failure.getMessage());
        }
    }

    private RuntimeContinuationCommandPort.ContinuationCommand decode(RuntimeTurnCommand command) throws java.io.IOException {
        JsonNode payload;
        try (JsonParser parser = objectMapper.createParser(command.payloadJson())) {
            payload = objectMapper.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(parser);
            if (parser.nextToken() != null) throw new PermanentFollowUpFailure("continuation payload has trailing JSON");
        }
        if (payload == null || !payload.isObject()) {
            throw new PermanentFollowUpFailure("continuation payload must be an object");
        }
        java.util.Set<String> fields = new java.util.HashSet<>();
        payload.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(java.util.Set.of("kind", "trigger", "operationId", "turnId", "hostileTokenId"))) {
            throw new PermanentFollowUpFailure("continuation payload contains unknown properties");
        }
        if (!payload.path("kind").isTextual() || !kind.name().equals(payload.path("kind").asText())) {
            throw new PermanentFollowUpFailure("continuation payload kind does not match typed adapter");
        }
        String trigger = requiredText(payload, "trigger");
        UUID operationId = requiredUuid(payload, "operationId");
        UUID payloadTurnId = requiredUuid(payload, "turnId");
        UUID hostileTokenId = requiredUuid(payload, "hostileTokenId");
        if (!command.turnId().equals(payloadTurnId)) {
            throw new PermanentFollowUpFailure("continuation payload turn id does not match command turn id");
        }
        MovementFollowUpRuntimeConsumer.Continuation continuation = new MovementFollowUpRuntimeConsumer.Continuation(
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.valueOf(kind.name()),
                trigger, operationId, payloadTurnId, hostileTokenId);
        if (!objectMapper.writeValueAsString(continuation).equals(command.payloadJson())) {
            throw new PermanentFollowUpFailure("continuation payload is not canonical");
        }
        return new RuntimeContinuationCommandPort.ContinuationCommand(command, continuation);
    }

    private static String requiredText(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || !payload.path(field).isTextual() || payload.path(field).asText().isBlank()) {
            throw new PermanentFollowUpFailure("continuation payload field " + field + " is required");
        }
        return payload.path(field).asText();
    }

    private static UUID requiredUuid(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        if (payload == null || !payload.isObject() || !payload.hasNonNull(field)
                || !payload.path(field).isTextual() || payload.path(field).asText().isBlank()) {
            throw new IllegalArgumentException("continuation payload field " + field + " is required");
        }
        try {
            return UUID.fromString(payload.path(field).asText());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("continuation payload field " + field + " is invalid", failure);
        }
    }
}
