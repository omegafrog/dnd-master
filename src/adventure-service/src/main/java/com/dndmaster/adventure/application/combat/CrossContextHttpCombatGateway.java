package com.dndmaster.adventure.application.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CrossContextHttpCombatGateway
        implements CharacterCombatPort, DiceCombatPort, CombatMapPort, AiCombatPort {
    private static final int GRID_DISTANCE_UNIT = 5;
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String internalToken;
    private final Map<java.util.UUID, CharacterSheetView> characterSheetViews = new ConcurrentHashMap<>();

    public CrossContextHttpCombatGateway(HttpClient client, URI baseUri, Duration timeout) {
        this(client, baseUri, timeout, "");
    }

    public CrossContextHttpCombatGateway(HttpClient client, URI baseUri, Duration timeout, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    /**
     * Character and map mutations are already committed by their idempotent
     * action steps. The terminal hook is an owning-service acknowledgement
     * boundary and deliberately does not copy foreign state locally.
     */
    @Override
    public void commitFinalState(CombatFinalizationCommand command) {
        Objects.requireNonNull(command, "combat finalization command must not be null");
    }

    @Override
    public void requireUsableCharacter(CombatActionCommand command) {
        CharacterSheetView character = readCharacterSheet(command);
        if (hasNoHitPoints(character.characterState())) {
            throw new RuntimeCombatRejectionException(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE);
        }
        characterSheetViews.put(command.operationId(), character);
    }

    @Override
    public Integer attackModifier(CombatActionCommand command) {
        CharacterSheetView character = characterSheetViews.computeIfAbsent(command.operationId(), ignored -> readCharacterSheet(command));
        try {
            JsonNode derived = objectMapper.readTree(character.derivedStatistics());
            JsonNode modifiers = derived.path("abilityModifiers");
            int strength = modifiers.path("strength").isInt()
                    ? modifiers.path("strength").asInt()
                    : Math.floorDiv(derived.path("abilityScores").path("strength").asInt(10) - 10, 2);
            return strength + 2 + Math.max(0, (character.level() - 1) / 4);
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public Integer damageAmount(CombatActionCommand command) {
        CharacterSheetView character = characterSheetViews.computeIfAbsent(command.operationId(), ignored -> readCharacterSheet(command));
        try {
            JsonNode attacks = objectMapper.readTree(character.derivedStatistics()).path("attacks");
            if (!attacks.isArray() || attacks.isEmpty()) return null;
            String damage = attacks.get(0).path("damage").asText("").replace(" ", "");
            java.util.regex.Matcher dice = java.util.regex.Pattern.compile("(\\d+)d(\\d+)([+-]\\d+)?").matcher(damage);
            if (dice.matches()) {
                int count = Integer.parseInt(dice.group(1));
                int sides = Integer.parseInt(dice.group(2));
                int bonus = dice.group(3) == null ? 0 : Integer.parseInt(dice.group(3));
                return Math.max(1, (count * (sides + 1)) / 2 + bonus);
            }
            java.util.regex.Matcher fixed = java.util.regex.Pattern.compile("(\\d+)([+-]\\d+)?").matcher(damage);
            if (fixed.matches()) {
                int base = Integer.parseInt(fixed.group(1));
                int bonus = fixed.group(2) == null ? 0 : Integer.parseInt(fixed.group(2));
                return Math.max(1, base + bonus);
            }
            return null;
        } catch (IOException | NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public String displayName(java.util.UUID characterSheetId, java.util.UUID ownerPlayerId, java.util.UUID sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-sheets/" + characterSheetId + "/runtime"))
                    .timeout(timeout).header("X-Internal-Token", internalToken).header("X-Session-ID", sessionId.toString())
                    .header("X-Owner-Player-ID", ownerPlayerId.toString()).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("character name read failed with status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), CharacterSheetView.class).characterName();
        } catch (IOException exception) {
            throw new CrossContextCallException("character name read failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("character name read interrupted", exception);
        }
    }

    private boolean hasNoHitPoints(String characterState) {
        if (characterState == null || characterState.isBlank()) return false;
        try {
            JsonNode state = objectMapper.readTree(characterState);
            JsonNode hitPoints = state == null ? null : state.get("currentHitPoints");
            return hitPoints != null && hitPoints.isNumber() && hitPoints.asInt() <= 0;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public void applyOutcome(CombatActionCommand command, CombatOutcome outcome) {
        Objects.requireNonNull(outcome, "combat outcome must not be null");
        if (!outcome.mutation().hasEffects()) return;
        try {
            CombatCharacterMutation mutation = outcome.mutation();
            RuntimeMutationRequest request = new RuntimeMutationRequest(mutation.hitPointDelta(), mutation.currencyDelta(), mutation.addItems(), mutation.removeItems());
            CharacterSheetView current = command.targetCharacterSheetId() == null
                    ? characterSheetViews.computeIfAbsent(command.operationId(), ignored -> readCharacterSheet(command))
                    : readCharacterSheet(command, command.targetCharacterSheetId());
        var target = command.targetCharacterSheetId() == null ? command.characterSheetId() : command.targetCharacterSheetId();
        HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-sheets/" + target.value() + "/runtime-mutations"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .header("X-Session-ID", command.sessionId().toString())
                    .header("X-Owner-Player-ID", Objects.requireNonNull(command.ownerPlayerId(), "owner player id must not be null").toString())
                    .header("Idempotency-Key", command.operationId().toString())
                    .header("If-Match-Version", Long.toString(current.version()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("character outcome update failed with status " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new CrossContextCallException("character outcome serialization failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("character outcome update interrupted", exception);
        }
    }

    private CharacterSheetView readCharacterSheet(CombatActionCommand command, com.dndmaster.adventure.domain.adventure.CharacterSheetId sheetId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-sheets/" + sheetId.value() + "/runtime"))
                    .timeout(timeout).header("X-Internal-Token", internalToken).header("X-Session-ID", command.sessionId().toString())
                    .header("X-Owner-Player-ID", Objects.requireNonNull(command.ownerPlayerId()).toString()).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new CrossContextCallException("target character read failed with status " + response.statusCode());
            return objectMapper.readValue(response.body(), CharacterSheetView.class);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CrossContextCallException("target character read failed", exception);
        }
    }

    @Override
    public int roll(CombatActionCommand command) {
        String value = send("rolls", "POST", command.role().name(), command);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new CrossContextCallException("Dice Roll BC returned malformed total", exception);
        }
    }

    @Override
    public int rollSpatialCheck(SpatialCheckRollCommand command) {
        Objects.requireNonNull(command, "spatial check roll command must not be null");
        try {
            PlayerCheckRollRequest request = new PlayerCheckRollRequest(command.adventureId(), command.ruleSetId().value(),
                    "PLAYER_ACTION", 1, 20, 0, command.sessionId(), command.operationId(), command.checkId(), command.expectedVersion());
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/dice-rolls/player"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("Idempotency-Key", command.checkId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("spatial check dice roll failed with status " + response.statusCode());
            }
            return objectMapper.readTree(response.body()).path("total").asInt(-1);
        } catch (IOException exception) {
            throw new CrossContextCallException("spatial check dice roll serialization failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("spatial check dice roll interrupted", exception);
        }
    }

    @Override
    public void validateAndMove(CombatActionCommand command) {
        move(new CombatMapMoveCommand(command, movementDistance(command), expectedMapVersion(command)));
    }

    @Override
    public CombatMapMoveResult move(CombatMapMoveCommand moveCommand) {
        CombatActionCommand command = moveCommand.action();
        if (command.combatMapId() == null || command.ownerPlayerId() == null || command.tokenId() == null) {
            throw new IllegalStateException("movement command requires ownerPlayerId and tokenId");
        }
        String appliedEdition = moveCommand.appliedEdition() == null
                ? characterSheetViews.computeIfAbsent(command.operationId(), ignored -> readCharacterSheet(command)).edition()
                : moveCommand.appliedEdition();
        List<PositionRequest> positions = movementPositions(command.movementPath());
        MoveRequest request = new MoveRequest(
                command.ownerPlayerId(), command.tokenId(), positions,
                moveCommand.distance(), appliedEdition, command.operationId(), moveCommand.expectedVersion(),
                moveCommand.previewFingerprint(), moveCommand.waypoints().stream().map(position -> new PositionRequest(position.x(), position.y())).toList());
        String route = "internal/v1/combat-maps/" + command.combatMapId() + "/movement-operations";
        Object body = new MovementOperationStartRequest(command.ownerPlayerId(), command.tokenId(), positions,
                moveCommand.distance(), appliedEdition, command.operationId(), moveCommand.expectedVersion(),
                moveCommand.previewFingerprint() == null ? "legacy:" + command.operationId() : moveCommand.previewFingerprint(),
                moveCommand.previewFingerprint(),
                moveCommand.waypoints().stream().map(position -> new PositionRequest(position.x(), position.y())).toList());
        String response = sendMovement(route, body, command);
        return movementResult(response, moveCommand.expectedVersion());
    }

    @Override
    public CombatMapPreviewResult preview(CombatMapPreviewCommand previewCommand) {
        PreviewRequest request = new PreviewRequest(previewCommand.ownerPlayerId(), previewCommand.tokenId(),
                new PositionRequest(previewCommand.destination().x(), previewCommand.destination().y()),
                previewCommand.waypoints().stream().map(position -> new PositionRequest(position.x(), position.y())).toList(),
                previewCommand.appliedEdition(), previewCommand.expectedVersion());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve(
                            "internal/v1/combat-maps/" + previewCommand.mapId() + "/movement-previews"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 409 || response.statusCode() == 422) {
                    throw new CombatMapMovementPreviewRejectedException(response.statusCode(), previewErrorCode(response.body()));
                }
                throw new CrossContextCallException("combat map movement preview failed with status " + response.statusCode());
            }
            PreviewResponse result = objectMapper.readValue(response.body(), PreviewResponse.class);
            return new CombatMapPreviewResult(previewCommand.mapId(), result.orderedPositions().stream()
                    .map(position -> new CombatMapPreviewPosition(position.x(), position.y())).toList(),
                    result.distance(), result.baseMapVersion(), result.fingerprint());
        } catch (IOException exception) {
            throw new CrossContextCallException("combat map movement preview transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("combat map movement preview interrupted", exception);
        }
    }

    @Override public CombatMapMoveResult movementOperation(java.util.UUID mapId, java.util.UUID operationId) { return operationRequest(mapId, operationId, "GET"); }
    @Override public CombatMapMoveResult latestMovementOperation(java.util.UUID mapId) { return latestOperationRequest(mapId); }
    @Override public CombatMapMoveResult resumeMovementOperation(java.util.UUID mapId, java.util.UUID operationId) { return operationRequest(mapId, operationId, "POST", null); }
    @Override public CombatMapMoveResult resumeMovementOperation(java.util.UUID mapId, java.util.UUID operationId, CombatMapCheckSubmission submission) { return operationRequest(mapId, operationId, "POST", submission); }
    @Override public CombatMapMoveResult cancelMovementOperation(java.util.UUID mapId, java.util.UUID operationId) { return operationRequest(mapId, operationId, "DELETE"); }

    @Override public CombatMapSpatialResult observe(CombatMapSpatialActionCommand command) {
        return spatialRequest(command.mapId(), "observe", command.commandId(), new SpatialActionRequest(command.ownerPlayerId(), command.tokenId(),
                command.cell().x(), command.cell().y(), command.expectedVersion(), command.commandId()));
    }

    @Override public CombatMapSpatialResult interact(CombatMapSpatialActionCommand command) {
        return spatialRequest(command.mapId(), "interact", command.commandId(), new SpatialActionRequest(command.ownerPlayerId(), command.tokenId(),
                command.cell().x(), command.cell().y(), command.expectedVersion(), command.commandId()));
    }

    @Override public CombatMapSpatialResult combatTurnStart(CombatMapSpatialTurnCommand command) {
        return spatialRequest(command.mapId(), "combat-turn-start", command.commandId(), new SpatialTurnRequest(command.ownerPlayerId(),
                command.expectedVersion(), command.commandId()));
    }

    @Override public CombatMapSpatialResult advanceDurations(CombatMapSpatialTurnCommand command) {
        return spatialRequest(command.mapId(), "advance-durations", command.commandId(), new SpatialTurnRequest(command.ownerPlayerId(),
                command.expectedVersion(), command.commandId()));
    }

    private CombatMapSpatialResult spatialRequest(java.util.UUID mapId, String action, java.util.UUID commandId, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/spatial/" + action))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .header("Idempotency-Key", commandId.toString()).POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("combat map spatial action failed with status " + response.statusCode());
            }
            JsonNode value = objectMapper.readTree(response.body());
            List<String> events = new ArrayList<>();
            value.path("publicEvents").forEach(event -> events.add(event.asText()));
            CombatMapPendingCheck pendingCheck = spatialPendingCheck(value.path("pendingCheck"));
            return new CombatMapSpatialResult(java.util.UUID.fromString(value.path("mapId").asText()),
                    value.path("mapVersion").asLong(), events,
                    value.hasNonNull("operationId") ? java.util.UUID.fromString(value.path("operationId").asText()) : null,
                    value.hasNonNull("status") ? value.path("status").asText() : null, pendingCheck);
        } catch (IOException exception) {
            throw new CrossContextCallException("combat map spatial action transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("combat map spatial action interrupted", exception);
        }
    }
    private CombatMapPendingCheck spatialPendingCheck(JsonNode pending) {
        return pending != null && pending.isObject() && pending.hasNonNull("checkId")
                ? new CombatMapPendingCheck(java.util.UUID.fromString(pending.path("checkId").asText()),
                        java.util.UUID.fromString(pending.path("operationId").asText()), pending.path("label").asText("판정"),
                        pending.path("diceExpression").asText("d20"), java.util.UUID.fromString(pending.path("ownerPlayerId").asText()),
                        CombatMapCheckActor.valueOf(pending.path("actor").asText("PLAYER"))) : null;
    }
    private CombatMapMoveResult operationRequest(java.util.UUID mapId, java.util.UUID operationId, String method) { return operationRequest(mapId, operationId, method, null); }
    private CombatMapMoveResult operationRequest(java.util.UUID mapId, java.util.UUID operationId, String method, CombatMapCheckSubmission submission) {
        try {
            String route = "internal/v1/combat-maps/" + mapId + "/movement-operations/" + operationId + ("POST".equals(method) ? "/resume" : "");
            HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(route)).timeout(timeout).header("X-Internal-Token", internalToken);
            if ("POST".equals(method)) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(submission == null ? "" : objectMapper.writeValueAsString(submission)));
            else if ("DELETE".equals(method)) request.DELETE(); else request.GET();
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 409 || response.statusCode() == 422) {
                    throw new CombatMapMovementPreviewRejectedException(response.statusCode(), previewErrorCode(response.body()));
                }
                throw new CrossContextCallException("combat map movement operation failed with status " + response.statusCode());
            }
            return movementResult(response.body(), 0);
        } catch (IOException exception) { throw new CrossContextCallException("combat map movement operation transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CrossContextCallException("combat map movement operation interrupted", exception); }
    }

    private CombatMapMoveResult latestOperationRequest(java.util.UUID mapId) {
        try {
            String route = "internal/v1/combat-maps/" + mapId + "/movement-operations";
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(route)).timeout(timeout)
                    .header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 409 || response.statusCode() == 422) {
                    throw new CombatMapMovementPreviewRejectedException(response.statusCode(), previewErrorCode(response.body()));
                }
                throw new CrossContextCallException("latest combat map movement operation failed with status " + response.statusCode());
            }
            return movementResult(response.body(), 0);
        } catch (IOException exception) { throw new CrossContextCallException("latest combat map movement operation transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CrossContextCallException("latest combat map movement operation interrupted", exception); }
    }

    private String previewErrorCode(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody == null ? "" : responseBody);
            String code = body == null ? "" : body.path("code").asText();
            return code.isBlank() ? "MOVEMENT_PREVIEW_REJECTED" : code;
        } catch (IOException ignored) {
            return "MOVEMENT_PREVIEW_REJECTED";
        }
    }

    private String sendMovement(String path, Object body, CombatActionCommand command) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("Idempotency-Key", command.operationId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 409 || response.statusCode() == 422) {
                    throw new CombatMapMovementPreviewRejectedException(response.statusCode(), previewErrorCode(response.body()));
                }
                throw new CrossContextCallException("combat map movement failed with status " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new CrossContextCallException("combat map movement transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("combat map movement interrupted", exception);
        }
    }

    private static int movementDistance(CombatActionCommand command) {
        return command.movementPath() == null ? 0 : Math.max(0, movementPositions(command.movementPath()).size() - 1) * GRID_DISTANCE_UNIT;
    }

    private static long expectedMapVersion(CombatActionCommand command) {
        return command.mapVersion() == null ? command.expectedVersion() : command.mapVersion();
    }

    private long mapVersion(String response, long fallback) {
        if (response == null || response.isBlank()) return fallback + 1;
        try {
            JsonNode body = objectMapper.readTree(response);
            return body != null && body.has("version") ? body.get("version").asLong() : fallback + 1;
        } catch (IOException exception) {
            throw new CrossContextCallException("combat map returned malformed movement result", exception);
        }
    }
    private CombatMapMoveResult movementResult(String response, long fallback) {
        try {
            JsonNode body = objectMapper.readTree(response);
            String outcomeStatus = body.hasNonNull("outcomeStatus")
                    ? body.path("outcomeStatus").asText() : body.path("status").asText("RETRY_WAIT");
            CombatMapMovementStatus status = CombatMapMovementStatus.fromCombatMapStatus(outcomeStatus);
            long version = body.hasNonNull("mapVersion") ? body.path("mapVersion").asLong() : fallback;
            java.util.UUID operationId = body.hasNonNull("operationId") ? java.util.UUID.fromString(body.path("operationId").asText()) : null;
            java.util.List<CombatMapPreviewPosition> traversed = new java.util.ArrayList<>();
            for (JsonNode position : body.path("traversedPath")) traversed.add(new CombatMapPreviewPosition(position.path("x").asInt(), position.path("y").asInt()));
            java.util.List<CombatMapPreviewPosition> requested = new java.util.ArrayList<>();
            for (JsonNode position : body.path("requestedPath")) requested.add(new CombatMapPreviewPosition(position.path("x").asInt(), position.path("y").asInt()));
            JsonNode finalPosition = body.path("finalPosition");
            CombatMapPreviewPosition finalCell = finalPosition.isObject() ? new CombatMapPreviewPosition(finalPosition.path("x").asInt(), finalPosition.path("y").asInt()) : null;
            java.util.List<String> events = new java.util.ArrayList<>();
            for (JsonNode event : body.path("publicEvents")) events.add(event.asText());
            JsonNode pending = body.path("pendingCheck");
            CombatMapPendingCheck pendingCheck = pending.isObject() && pending.hasNonNull("checkId")
                    ? new CombatMapPendingCheck(java.util.UUID.fromString(pending.path("checkId").asText()),
                            java.util.UUID.fromString(pending.path("operationId").asText()), pending.path("label").asText("판정"),
                            pending.path("diceExpression").asText("d20"),
                            java.util.UUID.fromString(pending.path("ownerPlayerId").asText()),
                            CombatMapCheckActor.valueOf(pending.path("actor").asText("PLAYER"))) : null;
            JsonNode details = body.path("pendingCheckDetails");
            CombatMapCheckDetails pendingCheckDetails = details.isObject() && details.hasNonNull("checkId")
                    ? new CombatMapCheckDetails(java.util.UUID.fromString(details.path("checkId").asText()),
                            java.util.UUID.fromString(details.path("operationId").asText()),
                            details.path("ruleReference").asText(),
                            details.hasNonNull("difficulty") ? details.path("difficulty").asInt() : null,
                            java.util.UUID.fromString(details.path("ownerPlayerId").asText()),
                            CombatMapCheckActor.valueOf(details.path("actor").asText("PLAYER"))) : null;
            return new CombatMapMoveResult(version, operationId, status, requested, traversed, finalCell, events,
                    body.hasNonNull("interruptionReason") ? body.path("interruptionReason").asText() : null, pendingCheck,
                    pendingCheckDetails);
        } catch (IOException exception) { throw new CrossContextCallException("combat map returned malformed movement result", exception); }
    }

    @Override
    public void controlState(CombatActionCommand command) {
        if (command.combatMapId() == null || command.ownerPlayerId() == null || command.tokenId() == null) {
            throw new IllegalStateException("ai state command requires ownerPlayerId and tokenId");
        }
        List<PositionRequest> positions = movementPositions(command.movementPath());
        PositionRequest position = positions.isEmpty() ? new PositionRequest(0, 0) : positions.getLast();
        long expectedVersion = command.expectedVersion() + (positions.isEmpty() ? 0 : 1);
        send("internal/v1/combat-maps/" + command.combatMapId() + "/ai-state", "POST",
                new AiStateRequest(command.ownerPlayerId(), command.tokenId(), position.x(), position.y(),
                        aiStateCommandId(command.operationId()), expectedVersion, List.of()),
                command);
    }

    private static java.util.UUID aiStateCommandId(java.util.UUID operationId) {
        return java.util.UUID.nameUUIDFromBytes((operationId + "|ai-state").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String adjudicate(CombatActionCommand command, int diceTotal) {
        return send("ai/adjudications", "POST", command.ruleSetId().value() + ":" + diceTotal, command).trim();
    }

    private String send(String path, String method, String body, CombatActionCommand command) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(timeout)
                .header("X-Internal-Token", internalToken)
                .header("Idempotency-Key", command.operationId().toString());
        if (method.equals("GET")) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("cross-context call failed with status " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new CrossContextCallException("cross-context transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("cross-context call interrupted", exception);
        }
    }

    private String send(String path, String method, Object body, CombatActionCommand command) {
        try {
            return send(path, method, body == null ? null : objectMapper.writeValueAsString(body), command);
        } catch (IOException exception) {
            throw new CrossContextCallException("cross-context payload serialization failed", exception);
        }
    }

    private CharacterSheetView readCharacterSheet(CombatActionCommand command) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            baseUri.resolve("internal/v1/character-sheets/" + command.characterSheetId().value()
                                    + "/runtime"))
                    .timeout(timeout)
                    .header("Idempotency-Key", command.operationId().toString())
                    .header("X-Internal-Token", internalToken)
                    .header("X-Session-ID", command.sessionId().toString());
            if (command.ownerPlayerId() != null) {
                builder.header("X-Owner-Player-ID", command.ownerPlayerId().toString());
            }
            HttpRequest request = builder.GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("cross-context call failed with status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), CharacterSheetView.class);
        } catch (IOException exception) {
            throw new CrossContextCallException("cross-context transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("cross-context call interrupted", exception);
        }
    }

    private static List<PositionRequest> movementPositions(String movementPath) {
        if (movementPath == null) {
            return List.of();
        }
        List<PositionRequest> positions = new ArrayList<>();
        for (String step : movementPath.split(">|;")) {
            positions.add(parsePosition(step));
        }
        return positions;
    }

    private static PositionRequest parsePosition(String value) {
        String trimmed = value.trim().toUpperCase();
        if (trimmed.contains(",")) {
            String[] coordinates = trimmed.split(",");
            return new PositionRequest(Integer.parseInt(coordinates[0].trim()), Integer.parseInt(coordinates[1].trim()));
        }
        int split = 0;
        while (split < trimmed.length() && Character.isLetter(trimmed.charAt(split))) split++;
        if (split == 0 || split == trimmed.length()) {
            throw new IllegalArgumentException("movement path must use grid labels like A1>B1");
        }
        int x = 0;
        for (int i = 0; i < split; i++) {
            x = x * 26 + (trimmed.charAt(i) - 'A' + 1);
        }
        x--;
        int y = Integer.parseInt(trimmed.substring(split)) - 1;
        return new PositionRequest(x, y);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CharacterSheetView(String edition, long version, String characterName, int level, boolean inspiration,
            String race, String characterClass, String background, String startingAbilities, String derivedStatistics,
            String characterBuild, String characterState) {}
    private record CharacterSheetRequest(java.util.UUID adventureId, java.util.UUID ownerPlayerId, String edition,
            String characterName, int level, boolean inspiration, String race, String characterClass, String background,
            String startingAbilities, String derivedStatistics, String characterBuild, String characterState,
            java.util.Map<String, String> blueprintValues) {}
    private record RuntimeMutationRequest(int hitPointDelta, int currencyDelta, List<String> addItems, List<String> removeItems) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    private record MoveRequest(
            java.util.UUID playerId, java.util.UUID tokenId, List<PositionRequest> positions, int distance,
            String appliedEdition, java.util.UUID commandId, long expectedVersion,
            String fingerprint, List<PositionRequest> waypoints) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    private record MovementOperationStartRequest(
            java.util.UUID playerId, java.util.UUID tokenId, List<PositionRequest> positions, int distance,
            String appliedEdition, java.util.UUID commandId, long expectedVersion, String fingerprint,
            String previewFingerprint, List<PositionRequest> waypoints) {}
    private record PreviewRequest(java.util.UUID playerId, java.util.UUID tokenId, PositionRequest destination,
            List<PositionRequest> waypoints, String appliedEdition, long expectedVersion) {}
    private record PreviewResponse(List<PositionRequest> orderedPositions, int distance, long baseMapVersion, String fingerprint) {}
    private record PositionRequest(int x, int y) {}
    private record SpatialActionRequest(java.util.UUID ownerId, java.util.UUID tokenId, int x, int y,
            long expectedVersion, java.util.UUID commandId) {}
    private record SpatialTurnRequest(java.util.UUID ownerId, long expectedVersion, java.util.UUID commandId) {}
    private record PlayerCheckRollRequest(java.util.UUID adventureId, java.util.UUID ruleSetId, String scope,
            int count, int sides, int modifier, java.util.UUID sessionId, java.util.UUID turnId,
            java.util.UUID commandId, long expectedVersion) {}
    private record AiStateRequest(
            java.util.UUID ownerId, java.util.UUID tokenId, int x, int y, java.util.UUID commandId,
            long expectedVersion, List<LayerRequest> layers) {}
    private record LayerRequest(String type, String value, String visibility) {}
}
