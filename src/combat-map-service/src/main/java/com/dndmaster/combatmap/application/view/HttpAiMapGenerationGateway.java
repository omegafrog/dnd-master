package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapLayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/** Calls the AI Game Master map contract and converts its proposal to map data. */
public final class HttpAiMapGenerationGateway implements AiMapGenerationPort {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpAiMapGenerationGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client;
        this.endpoint = baseUri.resolve("internal/v1/gm/maps");
        this.timeout = timeout;
        this.mapper = mapper;
        this.internalToken = internalToken;
    }

    @Override
    public PreparedMapData generate(String scenarioDescription) {
        return generate(new MapGenerationRequest(scenarioDescription, ""));
    }

    @Override
    public PreparedMapData generate(MapGenerationRequest request) {
        try {
            String mapData = mapper.writeValueAsString(java.util.Map.of(
                    "gridWidth", request.gridWidth(),
                    "gridHeight", request.gridHeight(),
                    "authoredObstacles", request.authoredObstacles().stream().map(HttpAiMapGenerationGateway::position).toList(),
                    "authoredDoors", request.authoredDoors().stream().map(door -> position(door.position())).toList(),
                    "authoredPlayerStart", request.authoredPlayerStart() == null ? "" : position(request.authoredPlayerStart()),
                    "mapImageAvailable", request.mapImage() != null));
            String body = mapper.writeValueAsString(new Request(request.selectedScenario(), request.currentContext(), mapData,
                    request.mapImage() == null ? "" : request.mapImage().dataUri()));
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken == null ? "" : internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI Game Master map proposal failed with status " + response.statusCode());
            }
            return toPreparedMap(mapper.readTree(response.body()), request);
        } catch (IOException exception) {
            throw new IllegalStateException("AI Game Master map proposal transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI Game Master map proposal interrupted", exception);
        }
    }

    private static PreparedMapData toPreparedMap(JsonNode root, MapGenerationRequest request) {
        int width = Math.max(1, root.path("width").asInt(request.gridWidth()));
        int height = Math.max(1, root.path("height").asInt(request.gridHeight()));
        width = Math.max(width, minimumWidth(request));
        height = Math.max(height, minimumHeight(request));
        Set<GridPosition> obstacles = parsePositions(root.path("obstacles"), width, height, "obstacles");
        List<Door> doors = parseDoors(root.path("doors"), width, height);
        List<String> boundaries = parseBoundaries(root.path("boundaries"), width, height);
        for (Door door : doors) {
            if (obstacles.contains(door.position())) throw new IllegalArgumentException("AI map proposal door is blocked");
            obstacles.remove(door.position());
        }
        String playerStart = root.path("playerStart").asText("").trim();
        if (playerStart.isBlank() && request.authoredPlayerStart() != null) {
            playerStart = position(request.authoredPlayerStart());
        }
        if (!playerStart.isBlank()) {
            GridPosition position = parsePosition(playerStart, width, height, "playerStart");
            if (obstacles.contains(position) || doors.stream().anyMatch(door -> door.position().equals(position))) {
                throw new IllegalArgumentException("AI map proposal player start is blocked");
            }
        }
        List<MapLayer> layers = new ArrayList<>();
        if (request.mapImage() != null) {
            layers.add(new MapLayer("MAP_IMAGE", request.mapImage().dataUri(), LayerVisibility.PLAYER_VISIBLE));
            layers.add(new MapLayer("GRID_BOUNDS", initialGridBounds(request, width, height), LayerVisibility.PLAYER_VISIBLE));
        }
        layers.add(new MapLayer("GRID_SOURCE", "GM_PROPOSED", LayerVisibility.PLAYER_VISIBLE));
        if (!boundaries.isEmpty()) layers.add(new MapLayer("MAP_BOUNDARIES", String.join(";", boundaries), LayerVisibility.PLAYER_VISIBLE));
        if (!playerStart.isBlank()) layers.add(new MapLayer("GM_PLAYER_START", playerStart, LayerVisibility.AI_ONLY));
        String rationale = root.path("rationale").asText("").trim();
        if (!rationale.isBlank()) layers.add(new MapLayer("GM_MAP_RATIONALE", rationale, LayerVisibility.AI_ONLY));
        return new PreparedMapData(new GridSpec(width, height, request.cellSize(), request.distanceUnit()),
                List.<CombatToken>of(), obstacles, layers, doors);
    }

    private static int minimumWidth(MapGenerationRequest request) {
        int obstacles = request.authoredObstacles().stream().mapToInt(GridPosition::x).max().orElse(-1);
        int doors = request.authoredDoors().stream().map(door -> door.position().x()).mapToInt(Integer::intValue).max().orElse(-1);
        int player = request.authoredPlayerStart() == null ? -1 : request.authoredPlayerStart().x();
        return Math.max(obstacles, Math.max(doors, player)) + 1;
    }

    private static int minimumHeight(MapGenerationRequest request) {
        int obstacles = request.authoredObstacles().stream().mapToInt(GridPosition::y).max().orElse(-1);
        int doors = request.authoredDoors().stream().map(door -> door.position().y()).mapToInt(Integer::intValue).max().orElse(-1);
        int player = request.authoredPlayerStart() == null ? -1 : request.authoredPlayerStart().y();
        return Math.max(obstacles, Math.max(doors, player)) + 1;
    }

    private static String initialGridBounds(MapGenerationRequest request, int gridWidth, int gridHeight) {
        int imageWidth = gridWidth * request.cellSize();
        int imageHeight = gridHeight * request.cellSize();
        try {
            var image = ImageIO.read(new ByteArrayInputStream(request.mapImage().content()));
            if (image != null) {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
            }
        } catch (IOException ignored) {
            // Keep the deterministic fallback dimensions when the image metadata is unavailable.
        }
        int cellSize = Math.max(1, Math.min(imageWidth / gridWidth, imageHeight / gridHeight));
        int boundsWidth = gridWidth * cellSize;
        int boundsHeight = gridHeight * cellSize;
        int originX = Math.max(0, (imageWidth - boundsWidth) / 2);
        int originY = Math.max(0, (imageHeight - boundsHeight) / 2);
        return originX + "," + originY + "," + boundsWidth + "," + boundsHeight + "," + imageWidth + "," + imageHeight;
    }

    private static Set<GridPosition> parsePositions(JsonNode values, int width, int height, String field) {
        if (!values.isArray()) throw new IllegalArgumentException("AI map proposal " + field + " must be an array");
        Set<GridPosition> result = new HashSet<>();
        for (JsonNode value : values) result.add(parsePosition(value.asText(), width, height, field));
        return result;
    }

    private static List<String> parseBoundaries(JsonNode values, int width, int height) {
        if (values.isMissingNode() || values.isNull()) return List.of();
        if (!values.isArray()) throw new IllegalArgumentException("AI map proposal boundaries must be an array");
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            try {
                String raw = value.isTextual() ? value.asText() : value.path("x").asText("") + "," + value.path("y").asText("") + "," + value.path("orientation").asText("") + "," + value.path("kind").asText("");
                com.dndmaster.combatmap.domain.MapBoundary boundary = com.dndmaster.combatmap.domain.MapBoundary.parse(raw);
                if (!boundary.inside(new GridSpec(width, height, 1, 1))) throw new IllegalArgumentException("outside grid");
                String encoded = boundary.encoded();
                if (!result.contains(encoded)) result.add(encoded);
            } catch (RuntimeException exception) { throw new IllegalArgumentException("AI map proposal boundaries contains an invalid edge", exception); }
        }
        return List.copyOf(result);
    }

    private static List<Door> parseDoors(JsonNode values, int width, int height) {
        if (!values.isArray()) throw new IllegalArgumentException("AI map proposal doors must be an array");
        List<Door> result = new ArrayList<>();
        for (JsonNode value : values) result.add(new Door(parsePosition(value.asText(), width, height, "doors"), false));
        return List.copyOf(result);
    }

    private static GridPosition parsePosition(String value, int width, int height, String field) {
        String[] pair = value == null ? new String[0] : value.trim().split(",", -1);
        if (pair.length != 2) throw new IllegalArgumentException("AI map proposal " + field + " contains an invalid cell");
        try {
            int x = Integer.parseInt(pair[0].trim());
            int y = Integer.parseInt(pair[1].trim());
            if (x < 0 || y < 0 || x >= width || y >= height) {
                throw new IllegalArgumentException("AI map proposal " + field + " contains an out-of-grid cell");
            }
            return new GridPosition(x, y);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("AI map proposal " + field + " contains an invalid cell", exception);
        }
    }

    private static String position(GridPosition position) { return position.x() + "," + position.y(); }

    private record Request(String selectedScenario, String currentContext, String mapData, String imageDataUri) {}
}
