package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapLayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpAiMapGenerationGateway.class);
    private final HttpClient client;
    private final URI endpoint;
    private final URI entryPlacementEndpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpAiMapGenerationGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client;
        this.endpoint = baseUri.resolve("internal/v1/gm/maps");
        this.entryPlacementEndpoint = baseUri.resolve("internal/v1/gm/map-entry-placement");
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
            String mapData = mapper.writeValueAsString(java.util.Map.ofEntries(
                    java.util.Map.entry("gridWidth", request.gridWidth()),
                    java.util.Map.entry("gridHeight", request.gridHeight()),
                    java.util.Map.entry("gridOriginX", request.gridOriginX()),
                    java.util.Map.entry("gridOriginY", request.gridOriginY()),
                    java.util.Map.entry("gridCellSize", request.gridCellSize()),
                    java.util.Map.entry("gridConfirmed", request.gridConfirmed()),
                    java.util.Map.entry("crop", request.crop()),
                    java.util.Map.entry("imageRevision", request.imageRevision()),
                    java.util.Map.entry("authoredObstacles", request.authoredObstacles().stream().map(HttpAiMapGenerationGateway::position).toList()),
                    java.util.Map.entry("authoredDoors", request.authoredDoors().stream().map(door -> position(door.position())).toList()),
                    java.util.Map.entry("authoredBoundaries", request.authoredBoundaries().stream().map(com.dndmaster.combatmap.domain.MapBoundary::encoded).toList()),
                    java.util.Map.entry("authoredPlayerStart", request.authoredPlayerStart() == null ? "" : position(request.authoredPlayerStart())),
                    java.util.Map.entry("mapImageAvailable", request.mapImage() != null)));
            String body = mapper.writeValueAsString(new Request(request.selectedScenario(), request.currentContext(), mapData,
                    request.mapImage() == null ? "" : request.mapImage().dataUri()));
            LOGGER.info("map_placement_agent_request scenario={} currentContext={} grid={}x{} cellSize={} distanceUnit={} origin=({}, {}) gridCellSize={} crop={} imageRevision={} mapImage={} authoredObstacles={} authoredDoors={} authoredBoundaries={}",
                    request.selectedScenario(), compactLogValue(request.currentContext()), request.gridWidth(), request.gridHeight(),
                    request.cellSize(), request.distanceUnit(), request.gridOriginX(), request.gridOriginY(), request.gridCellSize(),
                    compactLogValue(request.crop()), compactLogValue(request.imageRevision()), request.mapImage() != null,
                    request.authoredObstacles().size(), request.authoredDoors().size(), request.authoredBoundaries().size());
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
            JsonNode root = mapper.readTree(response.body());
            LOGGER.info("map_placement_agent_response scenario={} status={} responseChars={} responseBody={} dimensions={}x{} boundaries={} obstacles={} doors={} playerStart={} proposalStatus={} proposalPosition={} proposalConfidence={} proposalEvidence={} proposalSource={} rationale={}",
                    request.selectedScenario(), response.statusCode(), response.body().length(), compactLogValue(response.body()),
                    root.path("width").asInt(-1), root.path("height").asInt(-1), root.path("boundaries").size(), root.path("obstacles").size(),
                    root.path("doors").size(), compactLogValue(root.path("playerStart").asText("")),
                    compactLogValue(root.path("playerStartProposal").path("status").asText("")),
                    compactLogValue(root.path("playerStartProposal").path("position").asText("")),
                    root.path("playerStartProposal").path("confidence").asDouble(-1),
                    compactLogValue(root.path("playerStartProposal").path("evidence").toString()),
                    compactLogValue(root.path("playerStartProposal").path("source").asText("")),
                    compactLogValue(root.path("rationale").asText("")));
            return toPreparedMap(root, request, mapper);
        } catch (IOException exception) {
            throw new IllegalStateException("AI Game Master map proposal transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI Game Master map proposal interrupted", exception);
        }
    }

    @Override
    public PreparedMapData proposeEntryPlacement(MapGenerationRequest request) {
        try {
            String mapData = mapper.writeValueAsString(java.util.Map.ofEntries(
                    java.util.Map.entry("gridWidth", request.gridWidth()),
                    java.util.Map.entry("gridHeight", request.gridHeight()),
                    java.util.Map.entry("gridOriginX", request.gridOriginX()),
                    java.util.Map.entry("gridOriginY", request.gridOriginY()),
                    java.util.Map.entry("gridCellSize", request.gridCellSize()),
                    java.util.Map.entry("gridConfirmed", request.gridConfirmed()),
                    java.util.Map.entry("crop", request.crop()),
                    java.util.Map.entry("obstacles", request.authoredObstacles().stream().map(HttpAiMapGenerationGateway::position).toList()),
                    java.util.Map.entry("doors", request.authoredDoors().stream().map(door -> position(door.position())).toList()),
                    java.util.Map.entry("boundaries", request.authoredBoundaries().stream().map(com.dndmaster.combatmap.domain.MapBoundary::encoded).toList())));
            EntryPlacementRequest payload = new EntryPlacementRequest(
                    entryTargetScene(request.currentContext()), request.entryAction(), request.entryJudgment(), request.entryNarration(),
                    mapData, request.mapImage() == null ? "" : request.mapImage().dataUri());
            LOGGER.info("map_entry_localization_request scene={} action={} judgment={} narration={} mapData={} image={}",
                    compactLogValue(request.currentContext()), compactLogValue(request.entryAction()),
                    compactLogValue(request.entryJudgment()), compactLogValue(request.entryNarration()),
                    compactLogValue(mapData), request.mapImage() != null);
            HttpRequest httpRequest = HttpRequest.newBuilder(entryPlacementEndpoint)
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken == null ? "" : internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI Game Master entry placement failed with status " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode interpretation = root.path("entryInterpretation").isObject()
                    ? root.path("entryInterpretation") : root.path("interpretation");
            LOGGER.info("map_entry_localization_response status={} responseChars={} responseBody={} placementStatus={} transition={} targetScene={} anchor={} placementRelation={} candidates={} reason={}",
                    response.statusCode(), response.body().length(), compactLogValue(response.body()),
                    root.path("status").asText(""), compactLogValue(interpretation.path("transition").asText("")),
                    compactLogValue(interpretation.path("targetScene").asText("")),
                    compactLogValue(interpretation.path("anchor").asText("")),
                    compactLogValue(interpretation.path("placementRelation").asText("")), root.path("candidates").size(),
                    compactLogValue(root.path("reason").asText("")));
            return toEntryPlacement(root, request);
        } catch (IOException exception) {
            throw new IllegalStateException("AI Game Master entry placement transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI Game Master entry placement interrupted", exception);
        }
    }

    private PreparedMapData toEntryPlacement(JsonNode root, MapGenerationRequest request) throws IOException {
        List<MapEntryCandidate> candidates = new ArrayList<>();
        if (root.path("candidates").isArray()) {
            for (JsonNode candidate : root.path("candidates")) {
                if (!candidate.has("x") || !candidate.has("y")) continue;
                List<String> evidence = new ArrayList<>();
                if (candidate.path("evidence").isArray()) candidate.path("evidence").forEach(item -> evidence.add(item.asText()));
                candidates.add(new MapEntryCandidate(candidate.path("x").asInt(-1), candidate.path("y").asInt(-1),
                        candidate.path("confidence").asDouble(Double.NaN), candidate.path("source").asText("MAP_IMAGE"),
                        candidate.path("anchor").asText(""), candidate.path("reason").asText(""), evidence));
            }
        }
        List<MapEntryCandidate> valid = candidates.stream()
                .filter(candidate -> candidate.x() >= 0 && candidate.y() >= 0
                        && candidate.x() < request.gridWidth() && candidate.y() < request.gridHeight()
                        && Double.isFinite(candidate.confidence()) && candidate.confidence() >= 0 && candidate.confidence() <= 1)
                .limit(3).toList();
        List<MapLayer> layers = new ArrayList<>();
        if (!valid.isEmpty()) layers.add(new MapLayer("GM_PLAYER_START_PROPOSAL", mapper.writeValueAsString(proposal(valid.getFirst(), root)), LayerVisibility.AI_ONLY));
        layers.add(new MapLayer("GM_ENTRY_PLACEMENT_RESULT", mapper.writeValueAsString(root), LayerVisibility.AI_ONLY));
        return new PreparedMapData(new GridSpec(request.gridWidth(), request.gridHeight(), request.cellSize(), request.distanceUnit()),
                List.of(), Set.of(), layers);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode proposal(MapEntryCandidate candidate, JsonNode root) {
        var result = mapper.createObjectNode();
        result.put("position", candidate.x() + "," + candidate.y());
        result.put("confidence", candidate.confidence());
        result.put("source", candidate.source());
        // The dedicated placement agent uses RESOLVED/AMBIGUOUS for the
        // overall result. Once a candidate passed the transport-level grid
        // checks, this layer represents that candidate as a proposal for the
        // shared deterministic validator.
        result.put("status", "PROPOSED");
        List<String> evidence = new ArrayList<>(candidate.evidence());
        if (evidence.isEmpty()) {
            JsonNode interpretation = root.path("entryInterpretation").isObject()
                    ? root.path("entryInterpretation") : root.path("interpretation");
            JsonNode interpretationEvidence = interpretation.path("evidence");
            if (interpretationEvidence.isArray()) interpretationEvidence.forEach(item -> {
                if (!item.asText().isBlank()) evidence.add(item.asText());
            });
            else if (!interpretationEvidence.asText("").isBlank()) evidence.add(interpretationEvidence.asText());
        }
        if (evidence.isEmpty() && !candidate.reason().isBlank()) evidence.add(candidate.reason());
        result.set("evidence", mapper.valueToTree(evidence));
        result.put("anchor", candidate.anchor());
        result.put("reason", candidate.reason());
        return result;
    }

    private record EntryPlacementRequest(String targetScene, String action, String judgment, String narration,
                                         String mapData, String imageDataUri) {}
    private record MapEntryCandidate(int x, int y, double confidence, String source, String anchor,
                                     String reason, List<String> evidence) {}

    private static String entryTargetScene(String context) {
        if (context == null) return "unknown";
        for (String part : context.split(";")) {
            if (part.startsWith("scene=")) return part.substring("scene=".length()).trim();
        }
        return context.trim().isBlank() ? "unknown" : context.trim();
    }

    private static PreparedMapData toPreparedMap(JsonNode root, MapGenerationRequest request, ObjectMapper mapper) {
        int width = request.gridConfirmed() ? request.gridWidth() : Math.max(1, root.path("width").asInt(request.gridWidth()));
        int height = request.gridConfirmed() ? request.gridHeight() : Math.max(1, root.path("height").asInt(request.gridHeight()));
        width = Math.max(width, minimumWidth(request));
        height = Math.max(height, minimumHeight(request));
        Set<GridPosition> obstacles = parsePositions(root.path("obstacles"), width, height, "obstacles");
        Set<GridPosition> authoredObstacles = new HashSet<>(request.authoredObstacles());
        Set<GridPosition> authoredDoors = request.authoredDoors().stream().map(Door::position).collect(java.util.stream.Collectors.toSet());
        // User-authored cells are authoritative when the provider guessed a
        // conflicting obstacle or door. Provider-only conflicts remain an
        // invalid response and are rejected below.
        obstacles.removeAll(authoredDoors);
        obstacles.addAll(authoredObstacles);
        List<Door> doors = new ArrayList<>(parseDoors(root.path("doors"), width, height));
        doors.removeIf(door -> authoredObstacles.contains(door.position()));
        for (Door authored : request.authoredDoors()) {
            doors.removeIf(existing -> existing.position().equals(authored.position()));
            doors.add(authored);
        }
        List<String> boundaries = new ArrayList<>(request.authoredBoundaries().stream()
                .map(com.dndmaster.combatmap.domain.MapBoundary::encoded).toList());
        for (String boundary : parseBoundaries(root.path("boundaries"), width, height)) {
            // A user's saved line wins over an AI proposal at the same shared
            // cell side, even when the provider returned a different kind.
            if (boundaries.stream().noneMatch(existing -> sameBoundarySide(existing, boundary))) boundaries.add(boundary);
        }
        List<MapBoundaryCandidate> candidates = parseCandidates(root.path("candidates"), width, height);
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
            detectedContentCrop(request.mapImage()).ifPresent(crop -> layers.add(new MapLayer("MAP_CROP", crop, LayerVisibility.PLAYER_VISIBLE)));
        }
        layers.add(new MapLayer("GRID_SOURCE", "GM_PROPOSED", LayerVisibility.PLAYER_VISIBLE));
        if (!boundaries.isEmpty()) layers.add(new MapLayer("MAP_BOUNDARIES", String.join(";", boundaries), LayerVisibility.PLAYER_VISIBLE));
        if (!candidates.isEmpty()) {
            try { layers.add(new MapLayer("MAP_BOUNDARY_CANDIDATES", mapper.writeValueAsString(candidates), LayerVisibility.AI_ONLY)); }
            catch (IOException ignored) { /* candidate explanations are optional; boundary strings remain usable */ }
        }
        JsonNode proposal = root.path("playerStartProposal");
        if (proposal.isObject() && !proposal.path("position").asText("").isBlank()) {
            String proposalPosition = proposal.path("position").asText("").trim();
            GridPosition parsedProposal = parsePosition(proposalPosition, width, height, "playerStartProposal.position");
            if (obstacles.contains(parsedProposal) || doors.stream().anyMatch(door -> door.position().equals(parsedProposal))) {
                throw new IllegalArgumentException("AI map proposal player start proposal is blocked");
            }
            try { layers.add(new MapLayer("GM_PLAYER_START_PROPOSAL", mapper.writeValueAsString(proposal), LayerVisibility.AI_ONLY)); }
            catch (IOException ignored) { /* optional placement evidence */ }
        }
        String rationale = root.path("rationale").asText("").trim();
        if (!rationale.isBlank()) layers.add(new MapLayer("GM_MAP_RATIONALE", rationale, LayerVisibility.AI_ONLY));
        return new PreparedMapData(new GridSpec(width, height, request.cellSize(), request.distanceUnit()),
                List.<CombatToken>of(), obstacles, layers, doors, candidates);
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

    private static java.util.Optional<String> detectedContentCrop(MapImageEvidence image) {
        try {
            var decoded = ImageIO.read(new ByteArrayInputStream(image.content()));
            if (decoded == null) return java.util.Optional.empty();
            MapContentBounds bounds = new MapContentBoundsDetector().detect(decoded);
            return bounds.confidence() >= .10d
                    ? java.util.Optional.of(bounds.x() + "," + bounds.y() + "," + bounds.width() + "," + bounds.height())
                    : java.util.Optional.empty();
        } catch (IOException ignored) { return java.util.Optional.empty(); }
    }

    private static String compactLogValue(String value) {
        if (value == null) return "";
        String compact = value.replace("\r", "").replace("\n", "\\n").trim();
        return compact.length() <= 2000 ? compact : compact.substring(0, 2000) + "…";
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

    private static List<MapBoundaryCandidate> parseCandidates(JsonNode values, int width, int height) {
        if (values.isMissingNode() || values.isNull()) return List.of();
        if (!values.isArray()) throw new IllegalArgumentException("AI map proposal candidates must be an array");
        List<MapBoundaryCandidate> result = new ArrayList<>();
        for (JsonNode value : values) {
            int x = value.path("x").asInt(-1);
            int y = value.path("y").asInt(-1);
            String orientation = value.path("orientation").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            String kind = value.path("kind").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            boolean inside = ("HORIZONTAL".equals(orientation) && x >= 0 && x < width && y >= 0 && y <= height)
                    || ("VERTICAL".equals(orientation) && x >= 0 && x <= width && y >= 0 && y < height);
            if (!inside) throw new IllegalArgumentException("AI map proposal candidate is outside grid");
            List<String> evidence = new ArrayList<>();
            if (value.path("evidence").isArray()) for (JsonNode item : value.path("evidence")) evidence.add(item.asText());
            result.add(new MapBoundaryCandidate(x, y, orientation, kind,
                    value.path("confidence").asDouble(Double.NaN), evidence,
                    value.path("source").asText("IMAGE_RULES")));
        }
        return List.copyOf(result);
    }

    private static boolean sameBoundarySide(String left, String right) {
        String[] a = left.split(",", -1);
        String[] b = right.split(",", -1);
        return a.length >= 3 && b.length >= 3 && a[0].trim().equals(b[0].trim())
                && a[1].trim().equals(b[1].trim()) && a[2].trim().equalsIgnoreCase(b[2].trim());
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
