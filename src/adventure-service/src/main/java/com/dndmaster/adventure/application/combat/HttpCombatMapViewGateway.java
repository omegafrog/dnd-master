package com.dndmaster.adventure.application.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HttpCombatMapViewGateway implements CombatMapViewPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final Duration detectionTimeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpCombatMapViewGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this(client, baseUri, timeout, Duration.ofMinutes(5), mapper, internalToken);
    }

    public HttpCombatMapViewGateway(HttpClient client, URI baseUri, Duration timeout, Duration detectionTimeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client; this.baseUri = baseUri; this.timeout = timeout; this.detectionTimeout = detectionTimeout;
        this.mapper = mapper; this.internalToken = internalToken;
    }

    @Override
    public Optional<View> playerView(UUID adventureId, UUID ownerId) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                "internal/v1/adventures/" + adventureId + "/combat-map/player-view?ownerId=" + ownerId))
                .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 403) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map view failed");
            Payload payload = mapper.readValue(response.body(), Payload.class);
            return Optional.of(new View(payload.mapId(), new Grid(payload.grid().width(), payload.grid().height(), payload.grid().cellSize(), payload.grid().distanceUnit()),
                    payload.tokens(), payload.obstacles(), payload.doors(), payload.layers(), payload.current(), payload.explored(), payload.version(), startCandidates(payload.playerStartCandidates())));
        } catch (IOException exception) { throw new IllegalStateException("combat map view transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map view interrupted", exception); }
    }

    @Override
    public Optional<View> preparationView(UUID adventureId, UUID ownerId) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/adventures/" + adventureId + "/combat-map/preparation-view?ownerId=" + ownerId))
                .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 403) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map preparation view failed");
            Payload payload = mapper.readValue(response.body(), Payload.class);
            return Optional.of(new View(payload.mapId(), new Grid(payload.grid().width(), payload.grid().height(), payload.grid().cellSize(), payload.grid().distanceUnit()), payload.tokens(), payload.obstacles(), payload.doors(), payload.layers(), payload.current(), payload.explored(), payload.version(), startCandidates(payload.playerStartCandidates())));
        } catch (IOException exception) { throw new IllegalStateException("combat map preparation view transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map preparation view interrupted", exception); }
    }

    @Override
    public void calibrate(UUID mapId, UUID ownerId, long expectedVersion, int width, int height, int cellSize,
            int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {
        Calibration payload = new Calibration(ownerId, expectedVersion, width, height, cellSize, originX, originY,
                imageWidth, imageHeight, playerX, playerY);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/calibration"))
                .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .PUT(HttpRequest.BodyPublishers.ofString(write(payload))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map calibration failed");
        } catch (IOException exception) { throw new IllegalStateException("combat map calibration transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map calibration interrupted", exception); }
    }

    @Override
    public void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles, List<Door> doors, String crop) {
        updateLayout(mapId, ownerId, expectedVersion, commandId, obstacles, doors, List.of(), crop, null, "");
    }

    @Override
    public void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles, List<Door> doors, List<Boundary> boundaries, String crop) {
        updateLayout(mapId, ownerId, expectedVersion, commandId, obstacles, doors, boundaries, crop, null, "");
    }

    @Override
    public void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles,
            List<Door> doors, List<Boundary> boundaries, String crop, Long alignmentVersion, String imageRevision) {
        updateLayout(mapId, ownerId, expectedVersion, commandId, obstacles, doors, boundaries, crop, alignmentVersion, imageRevision, null);
    }

    @Override
    public void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles,
            List<Door> doors, List<Boundary> boundaries, String crop, Long alignmentVersion, String imageRevision,
            Position playerStart) {
        Layout payload = new Layout(ownerId, expectedVersion, commandId,
                obstacles == null ? List.of() : obstacles.stream().map(position -> position.x() + "," + position.y()).toList(),
                doors == null ? List.of() : doors.stream().map(door -> door.x() + "," + door.y()).toList(),
                boundaries == null ? List.of() : boundaries.stream().map(boundary -> boundary.x() + "," + boundary.y() + "," + boundary.orientation() + "," + boundary.kind() + "," + boundary.open()).toList(), crop, alignmentVersion, imageRevision, playerStart);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/layout"))
                .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .PUT(HttpRequest.BodyPublishers.ofString(write(payload))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) throw new IllegalStateException("combat map layout conflict");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map layout save failed");
        } catch (IOException exception) { throw new IllegalStateException("combat map layout transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map layout interrupted", exception); }
    }

    @Override
    public BoundaryProposal detectMapBoundaries(UUID mapId, UUID ownerId) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/detect-boundaries"))
                .timeout(detectionTimeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(write(new Detection(ownerId)))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) throw new IllegalStateException("combat map boundary detection conflict");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map boundary detection failed");
            DetectionResult result = mapper.readValue(response.body(), DetectionResult.class);
            return new BoundaryProposal(result.mapVersion(), result.obstacles().stream().map(HttpCombatMapViewGateway::position).toList(),
                    result.doors().stream().map(HttpCombatMapViewGateway::door).toList(), result.boundaries().stream().map(HttpCombatMapViewGateway::boundary).toList(), result.crop(),
                    result.candidates().stream().map(candidate -> new BoundaryCandidate(candidate.x(), candidate.y(), candidate.orientation(), candidate.kind(), candidate.confidence(), candidate.evidence(), candidate.source())).toList(),
                    result.alignmentVersion(), result.imageRevision());
        } catch (IOException exception) { throw new IllegalStateException("combat map boundary detection transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map boundary detection interrupted", exception); }
    }

    @Override
    public Alignment alignment(UUID mapId, UUID ownerId) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/alignment?ownerId=" + ownerId))
                .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map alignment load failed");
            AlignmentPayload payload = mapper.readValue(response.body(), AlignmentPayload.class);
            return new Alignment(payload.mapId(), payload.version(), payload.imageRevision(), payload.imageViewId(), payload.originX(), payload.originY(), payload.cellSize());
        } catch (IOException exception) { throw new IllegalStateException("combat map alignment transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map alignment interrupted", exception); }
    }

    @Override
    public Alignment applyAlignment(UUID mapId, UUID ownerId, AlignmentRequest alignment) {
        AlignmentPayload payload = new AlignmentPayload(mapId, 0, alignment.imageRevision(), null, alignment.originX(), alignment.originY(), alignment.cellSize(), ownerId, alignment.commandId(), alignment.expectedVersion());
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/alignment"))
                .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .PUT(HttpRequest.BodyPublishers.ofString(write(payload))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) throw new IllegalStateException("map grid alignment conflict");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map alignment save failed");
            AlignmentPayload result = mapper.readValue(response.body(), AlignmentPayload.class);
            return new Alignment(result.mapId(), result.version(), result.imageRevision(), result.imageViewId(), result.originX(), result.originY(), result.cellSize());
        } catch (IOException exception) { throw new IllegalStateException("combat map alignment transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map alignment interrupted", exception); }
    }

    @Override
    public byte[] alignmentImage(UUID mapId, UUID ownerId, String imageViewId) {
        String encodedImageViewId = URLEncoder.encode(imageViewId, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId
                + "/alignment/image?ownerId=" + ownerId + "&imageViewId=" + encodedImageViewId))
                .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) throw new IllegalStateException("public map image unavailable");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("public map image download failed");
            return response.body();
        } catch (IOException exception) { throw new IllegalStateException("public map image transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("public map image interrupted", exception); }
    }

    @Override
    public byte[] preparationImage(UUID mapId, UUID ownerId) {
        return preparationImage(mapId, ownerId, null, null);
    }

    @Override
    public byte[] preparationImage(UUID mapId, UUID ownerId, UUID sourceDocumentId, String sourceAssetLocator) {
        String query = "?ownerId=" + ownerId;
        if (sourceDocumentId != null && sourceAssetLocator != null && !sourceAssetLocator.isBlank()) {
            query += "&sourceDocumentId=" + sourceDocumentId + "&sourceAssetLocator="
                    + URLEncoder.encode(sourceAssetLocator, java.nio.charset.StandardCharsets.UTF_8);
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/preparation-image" + query))
                .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) throw new IllegalStateException("map preparation image unavailable");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("map preparation image failed");
            return response.body();
        } catch (IOException exception) { throw new IllegalStateException("map preparation image transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("map preparation image interrupted", exception); }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (IOException exception) { throw new IllegalStateException("combat map calibration serialization failed", exception); }
    }

    private record Payload(UUID mapId, Grid grid, List<Token> tokens, List<Obstacle> obstacles, List<Door> doors, List<Layer> layers,
            List<Position> current, List<Position> explored, long version, List<StartCandidatePayload> playerStartCandidates) {
        private Payload { playerStartCandidates = playerStartCandidates == null ? List.of() : List.copyOf(playerStartCandidates); }
    }
    private record StartCandidatePayload(int x, int y, double confidence, List<String> evidence, String source) {
        private StartCandidatePayload { evidence = evidence == null ? List.of() : List.copyOf(evidence); }
    }
    private record Calibration(UUID ownerId, long expectedVersion, int width, int height, int cellSize,
            int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {}
    private record Layout(UUID ownerId, long expectedVersion, UUID commandId, List<String> obstacles, List<String> doors,
                          List<String> boundaries, String crop, Long alignmentVersion, String imageRevision, Position playerStart) {}
    private record Detection(UUID ownerId) {}
    private record DetectionResult(long mapVersion, List<String> obstacles, List<String> doors, List<String> boundaries, String crop,
                                   List<BoundaryCandidatePayload> candidates, long alignmentVersion, String imageRevision) {
        private DetectionResult {
            obstacles = obstacles == null ? List.of() : List.copyOf(obstacles);
            doors = doors == null ? List.of() : List.copyOf(doors);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
            crop = crop == null ? "" : crop;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            imageRevision = imageRevision == null ? "" : imageRevision;
        }
    }
    private record BoundaryCandidatePayload(int x, int y, String orientation, String kind,
                                            double confidence, List<String> evidence, String source) {
        private BoundaryCandidatePayload {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            source = source == null || source.isBlank() ? "IMAGE_RULES" : source;
        }
    }
    private record AlignmentPayload(UUID mapId, long version, String imageRevision, String imageViewId, double originX, double originY, double cellSize,
                                    UUID ownerId, UUID commandId, long expectedVersion) {
        private AlignmentPayload(UUID mapId, long version, String imageRevision, double originX, double originY, double cellSize) {
            this(mapId, version, imageRevision, null, originX, originY, cellSize, null, null, 0);
        }
    }

    private static Position position(String value) {
        String[] parts = value.split(",", -1);
        return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    private static Door door(String value) {
        Position position = position(value);
        return new Door(position.x(), position.y(), false);
    }
    private static Boundary boundary(String value) {
        String[] parts = value.split(",", -1);
        return new Boundary(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2], parts[3], parts.length >= 5 && Boolean.parseBoolean(parts[4]));
    }
    private static List<StartCandidate> startCandidates(List<StartCandidatePayload> values) {
        return values == null ? List.of() : values.stream().map(value -> new StartCandidate(value.x(), value.y(), value.confidence(), value.evidence(), value.source())).toList();
    }
}
