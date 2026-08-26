package com.dndmaster.adventure.application.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
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
                    payload.tokens(), payload.obstacles(), payload.layers(), payload.current(), payload.explored(), payload.version()));
        } catch (IOException exception) { throw new IllegalStateException("combat map view transport failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat map view interrupted", exception); }
    }

    private record Payload(UUID mapId, Grid grid, List<Token> tokens, List<Obstacle> obstacles, List<Layer> layers,
            List<Position> current, List<Position> explored, long version) {}
}
