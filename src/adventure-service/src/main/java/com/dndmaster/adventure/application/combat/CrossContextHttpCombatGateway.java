package com.dndmaster.adventure.application.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CrossContextHttpCombatGateway
        implements CharacterCombatPort, DiceCombatPort, CombatMapPort, AiCombatPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<java.util.UUID, CharacterSheetView> characterSheetViews = new ConcurrentHashMap<>();

    public CrossContextHttpCombatGateway(HttpClient client, URI baseUri, Duration timeout) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override
    public void requireUsableCharacter(CombatActionCommand command) {
        characterSheetViews.put(command.operationId(), readCharacterSheet(command));
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
                command.ownerPlayerId(), command.tokenId(), positions, Math.max(0, positions.size() - 1),
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
        send("internal/v1/combat-maps/" + command.combatMapId() + "/ai-state", "POST",
                new AiStateRequest(command.ownerPlayerId(), command.tokenId(), position.x(), position.y(),
                        command.operationId(), command.expectedVersion(), List.of()),
                command);
    }

    @Override
    public String adjudicate(CombatActionCommand command, int diceTotal) {
        return send("ai/adjudications", "POST", command.ruleSetId().value() + ":" + diceTotal, command).trim();
    }

    private String send(String path, String method, String body, CombatActionCommand command) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(timeout)
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
            HttpRequest request = HttpRequest.newBuilder(
                            baseUri.resolve("internal/v1/character-sheets/" + command.characterSheetId().value()))
                    .timeout(timeout)
                    .header("Idempotency-Key", command.operationId().toString())
                    .GET()
                    .build();
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

    private record CharacterSheetView(String edition, long version) {}
    private record MoveRequest(
            java.util.UUID playerId, java.util.UUID tokenId, List<PositionRequest> positions, int distance,
            String appliedEdition, java.util.UUID commandId, long expectedVersion) {}
    private record PositionRequest(int x, int y) {}
    private record AiStateRequest(
            java.util.UUID ownerId, java.util.UUID tokenId, int x, int y, java.util.UUID commandId,
            long expectedVersion, List<LayerRequest> layers) {}
    private record LayerRequest(String type, String value, String visibility) {}
}
