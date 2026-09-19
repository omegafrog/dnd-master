package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.EnemyObservationRollPort;
import com.dndmaster.adventure.application.combat.CombatMapMovementPreviewRejectedException;
import com.dndmaster.adventure.application.combat.CombatMapPreviewCommand;
import com.dndmaster.adventure.application.combat.MapMovementCoordinator;
import com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adapts the durable map move command to Combat Map's authoritative command API. */
public final class CombatMapRuntimeTurnCommandAdapter implements RuntimeTurnCommandAdapter {
    private final MapMovementCoordinator movementCoordinator;
    private final ObjectMapper mapper;
    private final MovementFollowUpPolicy followUpPolicy;

    public CombatMapRuntimeTurnCommandAdapter(CombatMapPort mapPort, EnemyObservationRollPort enemyObservationRoll,
            ObjectMapper mapper, ResolutionPort resolution, MovementFollowUpPolicy followUpPolicy) {
        this.movementCoordinator = new MapMovementCoordinator(mapPort, new com.dndmaster.adventure.application.combat.DiceCombatPort() {
            @Override public int roll(com.dndmaster.adventure.application.combat.CombatActionCommand command) {
                throw new UnsupportedOperationException("combat dice roll is unavailable");
            }
            @Override public int rollSpatialCheck(com.dndmaster.adventure.application.combat.SpatialCheckRollCommand command) {
                return mapPort.rollSpatialCheck(command);
            }
        }, java.util.Objects.requireNonNull(resolution, "resolution port must not be null"),
                SpatialActionAuthorizationPort.requiredPlayerAction(), enemyObservationRoll);
        this.mapper = java.util.Objects.requireNonNull(mapper, "object mapper must not be null");
        this.followUpPolicy = java.util.Objects.requireNonNull(followUpPolicy, "follow-up policy must not be null");
    }

    @Override public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
        try {
            var payload = mapper.readTree(command.payloadJson());
            var context = mapper.readTree(command.targetContext());
            if (payload == null || !payload.isObject() || context == null || !context.isObject()) {
                throw new IllegalArgumentException("movement command payload must be objects");
            }
            JsonNode savedOutcome = readSavedOutcome(command);
            String savedStatus = savedOutcome == null ? "" : savedOutcome.path("status").asText();
            if ("CHECK_REQUIRED".equals(savedStatus)) {
                UUID operationId = requiredUuid(savedOutcome, "operationId");
                var pending = movementCoordinator.query(requiredUuid(context, "combatMapId"), operationId);
                if (pending.pendingCheckDetails() != null
                        && pending.pendingCheckDetails().actor() == com.dndmaster.adventure.application.combat.CombatMapCheckActor.ENEMY) {
                    var details = pending.pendingCheckDetails();
                    var roll = new com.dndmaster.adventure.application.combat.EnemyObservationRollCommand(
                            command.adventureId(), requiredUuid(context, "combatMapId"), command.sessionId(),
                            new RuleSetId(requiredUuid(context, "ruleSetId")), command.ownerPlayerId(), command.turnId(), details.checkId(),
                            details.operationId(), command.commandId(), details.ruleReference(), details.diceExpression(),
                            details.modifier(), details.difficulty(), requiredNonNegativeLong(context, "expectedVersion"));
                    var resumed = movementCoordinator.rollEnemyAndResume(roll);
                    String outcome = mapper.writeValueAsString(resumed);
                    return movementExecution(command.turnId(), resumed, outcome);
                }
                String outcome = mapper.writeValueAsString(pending);
                return movementExecution(command.turnId(), pending, outcome);
            }
            if ("RETRY_REQUIRED".equals(savedStatus)) {
                UUID operationId = requiredUuid(savedOutcome, "operationId");
                var resumed = movementCoordinator.resume(requiredUuid(context, "combatMapId"), operationId);
                String outcome = mapper.writeValueAsString(resumed);
                return movementExecution(command.turnId(), resumed, outcome);
            }
            JsonNode pathNode = payload.get("path");
            if (pathNode == null || !pathNode.isArray() || pathNode.size() < 2
                    || !"MOVE".equals(payload.path("action").asText())) {
                throw new IllegalArgumentException("movement command path is invalid");
            }
            List<String> pathPositions = new ArrayList<>();
            for (JsonNode position : pathNode) {
                pathPositions.add(requiredCoordinate(position));
            }
            String path = String.join(";", pathPositions);
            CombatActionCommand mapCommand = new CombatActionCommand(command.commandId(),
                    new AdventureId(command.adventureId()), command.sessionId(),
                    new RuleSetId(requiredUuid(context, "ruleSetId")),
                    new CharacterSheetId(requiredUuid(context, "characterSheetId")),
                    requiredUuid(context, "combatMapId"), CombatActorRole.PLAYER,
                    payload.path("action").asText(), path, command.ownerPlayerId(),
                    requiredUuid(context, "tokenId"), requiredNonNegativeLong(context, "expectedVersion"));
            String appliedEdition = requiredText(context, "appliedEdition");
            String previewFingerprint = requiredText(context, "fingerprint");
            int distance = requiredPositiveInt(context, "distance");
            JsonNode waypointNode = context.get("waypoints");
            if (waypointNode == null || !waypointNode.isArray() || waypointNode.size() > CombatMapPreviewCommand.MAX_WAYPOINTS) {
                throw new IllegalArgumentException("movement waypoints are required");
            }
            List<com.dndmaster.adventure.application.combat.CombatMapPreviewPosition> waypoints = new ArrayList<>();
            for (JsonNode position : waypointNode) {
                String encoded = requiredCoordinate(position);
                String[] values = encoded.split(",");
                waypoints.add(new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(
                        Integer.parseInt(values[0]), Integer.parseInt(values[1])));
            }
            var movement = movementCoordinator.resolve(new com.dndmaster.adventure.application.combat.CombatMapMoveCommand(
                    mapCommand, distance, requiredNonNegativeLong(context, "expectedVersion"), appliedEdition,
                    previewFingerprint, waypoints));
            String outcome = mapper.writeValueAsString(movement);
            return movementExecution(command.turnId(), movement, outcome);
        } catch (java.io.IOException malformed) {
            return RuntimeTurnCommandExecution.permanentFailure(malformed.getMessage());
        } catch (IllegalArgumentException malformed) {
            return RuntimeTurnCommandExecution.permanentFailure(malformed.getMessage());
        } catch (CombatMapMovementPreviewRejectedException rejected) {
            return RuntimeTurnCommandExecution.movementConflict(rejected.status(), rejected.code());
        } catch (RuntimeException transientFailure) {
            return RuntimeTurnCommandExecution.transientFailure(transientFailure.getMessage());
        }
    }

    private RuntimeTurnCommandExecution movementExecution(UUID turnId,
            com.dndmaster.adventure.application.combat.CombatMapMoveResult movement, String outcome) {
        validateMovementResult(movement);
        var followUp = (movement.interruptionReason() != null && movement.interruptionReason().equals("HOSTILE_OBSERVED"))
                || movement.publicEvents().contains("HOSTILE_OBSERVED")
                ? com.dndmaster.adventure.application.combat.MovementFollowUpCommand.forTrigger(movement.operationId(),
                        turnId, movement.hostileTokenId(), "HOSTILE_OBSERVED", followUpPolicy) : null;
        var enriched = followUp == null ? movement : movement.withFollowUp(followUp);
        String enrichedOutcome = followUp == null ? outcome : serialize(enriched);
        return switch (movement.status()) {
            case COMMITTED, INTERRUPTED -> RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, enrichedOutcome, enriched);
            case CHECK_REQUIRED, RETRY_REQUIRED -> RuntimeTurnCommandExecution.movement(
                    RuntimeTurnCommandExecution.Status.TRANSIENT_FAILURE, enrichedOutcome, enriched);
            case CANCELLED -> RuntimeTurnCommandExecution.movement(
                    RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, enrichedOutcome, enriched);
        };
    }

    private static void validateMovementResult(
            com.dndmaster.adventure.application.combat.CombatMapMoveResult movement) {
        boolean hostileObserved = "HOSTILE_OBSERVED".equals(movement.interruptionReason())
                || movement.publicEvents().contains("HOSTILE_OBSERVED");
        if (hostileObserved && movement.hostileTokenId() == null) {
            throw new IllegalArgumentException("movement result HOSTILE_OBSERVED requires hostileTokenId");
        }
    }

    private String serialize(com.dndmaster.adventure.application.combat.CombatMapMoveResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("movement follow-up serialization failed", failure);
        }
    }

    private JsonNode readSavedOutcome(RuntimeTurnCommand command) {
        if (!"combat-map.move".equals(command.commandType()) || command.outcomeJson().isBlank()) return null;
        try {
            JsonNode value = mapper.readTree(command.outcomeJson());
            return value == null || !value.isObject() ? null : value;
        } catch (java.io.IOException malformed) {
            throw new IllegalArgumentException("saved movement outcome is invalid", malformed);
        }
    }

    private static String requiredCoordinate(JsonNode position) {
        if (position == null || !position.isObject()) throw new IllegalArgumentException("movement coordinate is required");
        int x = requiredCoordinateValue(position, "x");
        int y = requiredCoordinateValue(position, "y");
        return x + "," + y;
    }

    private static int requiredCoordinateValue(JsonNode position, String field) {
        JsonNode value = position.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new IllegalArgumentException("movement coordinate " + field + " is invalid");
        }
        return value.asInt();
    }

    private static UUID requiredUuid(JsonNode object, String field) {
        try {
            return UUID.fromString(requiredText(object, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("movement field " + field + " is invalid", exception);
        }
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("movement field " + field + " is required");
        }
        return value.asText();
    }

    private static long requiredNonNegativeLong(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalArgumentException("movement field " + field + " is invalid");
        }
        return value.asLong();
    }

    private static int requiredPositiveInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) {
            throw new IllegalArgumentException("movement field " + field + " is invalid");
        }
        return value.asInt();
    }
}
