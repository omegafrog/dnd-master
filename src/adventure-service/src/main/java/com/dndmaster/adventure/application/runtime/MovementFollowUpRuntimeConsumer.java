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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consumes the durable movement event and records its typed Runtime continuation. */
public final class MovementFollowUpRuntimeConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MovementFollowUpRuntimeConsumer.class);
    private static final List<String> ADJUDICATION_FIELDS = List.of("commandId", "turnId", "operationId",
            "hostileTokenId", "trigger", "kind", "judgment", "narration", "playerInput",
            "waitingForPlayerInput", "combatStartRequested");
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
            validateOptionalIdentity(eventPayload, "hostileTokenId");
            validateIdentity(eventPayload, "turnId");
            MovementFollowUpCommand followUp = objectMapper.treeToValue(eventPayload, MovementFollowUpCommand.class);
            if (!expected.equals(followUp)) {
                throw new PermanentFollowUpFailure("movement follow-up event payload mismatch");
            }
            if (!source.turnId().equals(followUp.turnId())) {
                throw new PermanentFollowUpFailure("movement follow-up belongs to another turn");
            }
            MovementFollowUpCommand.Kind kind = requiredKind(eventPayload);
            if (kind != expected.kind()) {
                throw new PermanentFollowUpFailure("movement follow-up kind does not match selected typed result");
            }
            UUID continuationId = UUID.nameUUIDFromBytes(
                    ("movement-continuation:" + followUp.commandId()).getBytes(StandardCharsets.UTF_8));
            RuntimeTurnCommand existing = commands.findByCommandId(continuationId).orElse(null);
            if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
                return MovementFollowUpPort.Result.done(existing.outcomeJson());
            }
            if (existing != null && existing.executionStatus() == RuntimeTurnCommand.ExecutionStatus.GM_INTERVENTION_PENDING) {
                return MovementFollowUpPort.Result.playerInputRequired(existing.outcomeJson());
            }
            int order = commands.findByTurnId(source.turnId()).stream()
                    .mapToInt(RuntimeTurnCommand::executionOrder).max().orElse(source.executionOrder()) + 1;
            String payload = objectMapper.writeValueAsString(new Continuation(kind, followUp.trigger(), followUp.operationId(),
                    source.turnId(), followUp.hostileTokenId(), followUp.mapVersion(), followUp.traversedPath(),
                    followUp.finalPosition(), followUp.publicEvents(), followUp.playerCheck(), followUp.playerRoll()));
            RuntimeTurnCommand continuation = existing == null
                    ? RuntimeTurnCommand.create(source.turnId(), continuationId, source.adventureId(), source.sessionId(),
                            source.ownerPlayerId(), source.targetContext(),
                            "movement.continuation." + kind.name().toLowerCase(java.util.Locale.ROOT), payload, order)
                    : existing;
            if (existing == null) commands.save(continuation);
            RuntimeContinuationOutcome outcome;
            try {
                outcome = continuationPort.execute(continuation,
                        new Continuation(kind, followUp.trigger(), followUp.operationId(), source.turnId(), followUp.hostileTokenId(),
                                followUp.mapVersion(), followUp.traversedPath(), followUp.finalPosition(), followUp.publicEvents(),
                                followUp.playerCheck(), followUp.playerRoll()));
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
            String actualStructure = structureSummary(outcome.value());
            long schemaStarted = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                    "runtime_movement_gm_response_schema", "commandId=" + continuation.commandId()
                            + " turnId=" + source.turnId() + " expectedStructure=CombatContinuationCommand"
                            + " actualStructure=" + actualStructure);
            final CombatContinuationCommand adjudication;
            try {
                adjudication = objectMapper.readValue(outcome.value(), CombatContinuationCommand.class);
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "runtime_movement_gm_response_schema", "commandId=" + continuation.commandId()
                                + " turnId=" + source.turnId() + " expectedStructure=CombatContinuationCommand"
                                + " actualStructure=validated recoveryAction=persist_same_command", schemaStarted);
            } catch (JsonProcessingException malformed) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                        "runtime_movement_gm_response_schema", "commandId=" + continuation.commandId()
                                + " turnId=" + source.turnId() + " expectedStructure=CombatContinuationCommand"
                                + " actualStructure=" + actualStructure + " recoveryAction=retain_pending_same_command",
                        schemaStarted, malformed);
                throw malformed;
            }
            if (adjudication.waitingForPlayerInput()) {
                commands.save(continuation.movementInterventionPending(outcome.value()));
                return MovementFollowUpPort.Result.playerInputRequired(outcome.value());
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

    private String structureSummary(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null) return "root=missing";
            if (!root.isObject()) return "root=" + root.getNodeType() + " payloadChars=" + payload.length();
            List<String> present = new java.util.ArrayList<>();
            List<String> missing = new java.util.ArrayList<>();
            for (String field : ADJUDICATION_FIELDS) {
                JsonNode value = root.get(field);
                if (value == null) missing.add(field);
                else present.add(field + ":" + value.getNodeType());
            }
            int unknownFields = 0;
            var names = root.fieldNames();
            while (names.hasNext()) if (!ADJUDICATION_FIELDS.contains(names.next())) unknownFields++;
            return "root=OBJECT present=" + present + " missing=" + missing + " unknownFieldCount=" + unknownFields;
        } catch (IOException malformed) {
            return "root=INVALID_JSON payloadChars=" + payload.length();
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

    private static void validateOptionalIdentity(JsonNode payload, String field) {
        if (payload == null || !payload.isObject() || !payload.has(field)) {
            throw new PermanentFollowUpFailure("movement follow-up field " + field + " is required");
        }
        if (!payload.get(field).isNull()) validateIdentity(payload, field);
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
            UUID hostileTokenId, long mapVersion,
            java.util.List<com.dndmaster.adventure.application.combat.CombatMapPreviewPosition> traversedPath,
            com.dndmaster.adventure.application.combat.CombatMapPreviewPosition finalPosition,
            java.util.List<String> publicEvents, RuntimeCheckProposal playerCheck, Integer playerRoll) {
        public Continuation {
            traversedPath = java.util.List.copyOf(traversedPath == null ? java.util.List.of() : traversedPath);
            publicEvents = java.util.List.copyOf(publicEvents == null ? java.util.List.of() : publicEvents);
        }
        public Continuation(MovementFollowUpCommand.Kind kind, String trigger, UUID operationId, UUID turnId) {
            this(kind, trigger, operationId, turnId, null, 0, java.util.List.of(), null, java.util.List.of(), null, null);
        }
        public Continuation(MovementFollowUpCommand.Kind kind, String trigger, UUID operationId, UUID turnId,
                UUID hostileTokenId) {
            this(kind, trigger, operationId, turnId, hostileTokenId, 0, java.util.List.of(), null, java.util.List.of(), null, null);
        }
        public Continuation(MovementFollowUpCommand.Kind kind, String trigger, UUID operationId, UUID turnId,
                UUID hostileTokenId, long mapVersion,
                java.util.List<com.dndmaster.adventure.application.combat.CombatMapPreviewPosition> traversedPath,
                com.dndmaster.adventure.application.combat.CombatMapPreviewPosition finalPosition,
                java.util.List<String> publicEvents) {
            this(kind, trigger, operationId, turnId, hostileTokenId, mapVersion, traversedPath, finalPosition,
                    publicEvents, null, null);
        }
    }
}
