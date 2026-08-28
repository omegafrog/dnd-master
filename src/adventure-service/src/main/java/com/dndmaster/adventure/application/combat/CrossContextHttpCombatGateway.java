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

    @Override
    public void requireUsableCharacter(CombatActionCommand command) {
        CharacterSheetView character = readCharacterSheet(command);
        if (hasNoHitPoints(character.characterState())) {
            throw new RuntimeCombatRejectionException(RuntimeCombatRejectionException.ZERO_HIT_POINTS_MESSAGE);
        }
        characterSheetViews.put(command.operationId(), character);
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
    public void validateAndMove(CombatActionCommand command) {
        if (command.combatMapId() == null || command.ownerPlayerId() == null || command.tokenId() == null) {
            throw new IllegalStateException("movement command requires ownerPlayerId and tokenId");
        }
        String appliedEdition = characterSheetViews.computeIfAbsent(command.operationId(), ignored -> readCharacterSheet(command))
                .edition();
        List<PositionRequest> positions = movementPositions(command.movementPath());
        MoveRequest request = new MoveRequest(
                command.ownerPlayerId(), command.tokenId(), positions,
                Math.max(0, positions.size() - 1) * GRID_DISTANCE_UNIT,
                appliedEdition, command.operationId(), command.expectedVersion());
        send("internal/v1/combat-maps/" + command.combatMapId() + "/moves", "POST", request, command);
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
    private record MoveRequest(
            java.util.UUID playerId, java.util.UUID tokenId, List<PositionRequest> positions, int distance,
            String appliedEdition, java.util.UUID commandId, long expectedVersion) {}
    private record PositionRequest(int x, int y) {}
    private record AiStateRequest(
            java.util.UUID ownerId, java.util.UUID tokenId, int x, int y, java.util.UUID commandId,
            long expectedVersion, List<LayerRequest> layers) {}
    private record LayerRequest(String type, String value, String visibility) {}
}
