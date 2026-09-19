package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;

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
        try {
            com.fasterxml.jackson.databind.JsonNode payload;
            try (JsonParser parser = objectMapper.createParser(command.payloadJson())) {
                payload = objectMapper.reader()
                        .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                        .readTree(parser);
                if (parser.nextToken() != null) {
                    throw new PermanentFollowUpFailure("continuation payload has trailing JSON");
                }
            }
            Set<String> payloadFields = new HashSet<>();
            if (payload != null && payload.isObject()) {
                payload.fieldNames().forEachRemaining(payloadFields::add);
            }
            payloadFields.remove("kind");
            if (payload == null || !payload.isObject()
                    || !Set.of("trigger", "operationId", "turnId", "hostileTokenId")
                            .equals(payloadFields)) {
                throw new PermanentFollowUpFailure("continuation payload contains unknown properties");
            }
            UUID operationId = requiredUuid(payload, "operationId");
            UUID payloadTurnId = requiredUuid(payload, "turnId");
            UUID hostileTokenId = requiredUuid(payload, "hostileTokenId");
            if (!command.turnId().equals(payloadTurnId)) {
                return RuntimeTurnCommandExecution.permanentFailure("continuation payload turn id does not match command turn id");
            }
            MovementFollowUpRuntimeConsumer.Continuation typedPayload =
                    objectMapper.treeToValue(payload, MovementFollowUpRuntimeConsumer.Continuation.class);
            MovementFollowUpRuntimeConsumer.Continuation continuation = new MovementFollowUpRuntimeConsumer.Continuation(
                    com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.valueOf(kind.name()),
                    typedPayload.trigger(), operationId, payloadTurnId, hostileTokenId);
            RuntimeContinuationCommandPort.ContinuationCommand typed =
                    new RuntimeContinuationCommandPort.ContinuationCommand(command, continuation);
            return switch (kind) {
                case COMBAT -> port.combat(typed);
                case WARNING -> port.warning(typed);
                case DIALOGUE -> port.dialogue(typed);
                case CHASE -> port.chase(typed);
            };
        } catch (PermanentFollowUpFailure failure) {
            return RuntimeTurnCommandExecution.permanentFailure(failure.getMessage());
        } catch (java.io.IOException | RuntimeException failure) {
            return RuntimeTurnCommandExecution.permanentFailure("continuation payload identity is invalid");
        }
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
