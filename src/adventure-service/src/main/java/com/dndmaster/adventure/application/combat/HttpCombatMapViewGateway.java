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
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpCombatMapViewGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client = client; this.baseUri = baseUri; this.timeout = timeout; this.mapper = mapper; this.internalToken = internalToken;
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
                    payload.tokens(), payload.obstacles(), payload.doors(), payload.layers(), payload.current(), payload.explored(), payload.version()));
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
            return Optional.of(new View(payload.mapId(), new Grid(payload.grid().width(), payload.grid().height(), payload.grid().cellSize(), payload.grid().distanceUnit()), payload.tokens(), payload.obstacles(), payload.doors(), payload.layers(), payload.current(), payload.explored(), payload.version()));
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
        updateLayout(mapId, ownerId, expectedVersion, commandId, obstacles, doors, List.of(), crop);
    }

    @Override
    public void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles, List<Door> doors, List<Boundary> boundaries, String crop) {
        Layout payload = new Layout(ownerId, expectedVersion, commandId,
                obstacles == null ? List.of() : obstacles.stream().map(position -> position.x() + "," + position.y()).toList(),
                doors == null ? List.of() : doors.stream().map(door -> door.x() + "," + door.y()).toList(),
                boundaries == null ? List.of() : boundaries.stream().map(boundary -> boundary.x() + "," + boundary.y() + "," + boundary.orientation() + "," + boundary.kind() + "," + boundary.open()).toList(), crop);
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
    public void detectMapBoundaries(UUID mapId, UUID ownerId) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/detect-boundaries"))
                .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(write(new Detection(ownerId)))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) throw new IllegalStateException("combat map boundary detection conflict");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat map boundary detection failed");
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
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + mapId + "/preparation-image?ownerId=" + ownerId))
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
            List<Position> current, List<Position> explored, long version) {}
    private record Calibration(UUID ownerId, long expectedVersion, int width, int height, int cellSize,
            int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {}
    private record Layout(UUID ownerId, long expectedVersion, UUID commandId, List<String> obstacles, List<String> doors, List<String> boundaries, String crop) {}
    private record Detection(UUID ownerId) {}
    private record AlignmentPayload(UUID mapId, long version, String imageRevision, String imageViewId, double originX, double originY, double cellSize,
                                    UUID ownerId, UUID commandId, long expectedVersion) {
        private AlignmentPayload(UUID mapId, long version, String imageRevision, double originX, double originY, double cellSize) {
            this(mapId, version, imageRevision, null, originX, originY, cellSize, null, null, 0);
        }
    }
}
