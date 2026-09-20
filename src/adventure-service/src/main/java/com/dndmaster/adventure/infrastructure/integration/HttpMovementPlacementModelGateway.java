package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.combat.MovementPlacementModelPort;
import com.dndmaster.adventure.application.combat.CrossContextCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** AI 경계의 JSON을 Adventure 내부 목적지 후보로만 변환한다. */
public final class HttpMovementPlacementModelGateway implements MovementPlacementModelPort {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final String token;

    public HttpMovementPlacementModelGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper, String token) {
        this.client = Objects.requireNonNull(client); this.endpoint = baseUri.resolve("internal/v1/gm/movement-placements");
        this.timeout = Objects.requireNonNull(timeout); this.objectMapper = Objects.requireNonNull(objectMapper); this.token = token == null ? "" : token;
    }

    @Override public MovementPlacementProposal interpret(MovementPlacementContext context) {
        try {
            String body = objectMapper.writeValueAsString(new Request(context.sourceText(), context.publicMap(), context.currentPosition(), context.tacticalContext()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json");
            if (!token.isBlank()) builder.header("X-Internal-Token", token);
            HttpResponse<String> response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new CrossContextCallException("movement placement failed with status " + response.statusCode());
            Wire payload = objectMapper.readValue(response.body(), Wire.class);
            if (payload == null) throw new IllegalArgumentException("movement placement response must be an object");
            MovementPlacementModelPort.Position destination = payload.destination() == null ? null : new MovementPlacementModelPort.Position(payload.destination().x(), payload.destination().y());
            List<MovementPlacementModelPort.Candidate> candidates = payload.candidates() == null ? List.of() : payload.candidates().stream()
                    .filter(item -> item != null && item.destination() != null).map(item -> new MovementPlacementModelPort.Candidate(new MovementPlacementModelPort.Position(item.destination().x(), item.destination().y()), item.confidence(), item.reason())).toList();
            return new MovementPlacementProposal(payload.status(), destination, candidates, payload.playerMessage());
        } catch (IOException exception) { throw new CrossContextCallException("movement placement failed", exception); }
        catch (IllegalArgumentException exception) { throw new CrossContextCallException("movement placement returned an invalid response", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CrossContextCallException("movement placement interrupted", exception); }
    }

    private record Request(String sourceText, String publicMap, String currentPosition, String tacticalContext) {}
    private record Wire(String status, Point destination, List<CandidateWire> candidates, String playerMessage) {}
    private record Point(int x, int y) {}
    private record CandidateWire(Point destination, double confidence, String reason) {}
}
