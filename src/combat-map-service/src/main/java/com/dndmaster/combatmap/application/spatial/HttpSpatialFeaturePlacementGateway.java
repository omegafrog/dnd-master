package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.GridPosition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** AI Game Master 경계의 공간 요소 후보를 저장 권한 없는 제안으로 변환한다. */
public final class HttpSpatialFeaturePlacementGateway implements SpatialFeaturePlacementModelPort {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpSpatialFeaturePlacementGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client;
        this.endpoint = baseUri.resolve("internal/v1/gm/spatial-features");
        this.timeout = timeout;
        this.mapper = mapper;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    public SpatialFeaturePlacementProposal propose(SpatialFeaturePlacementContext context) {
        try {
            Request payload = new Request(context.storyPlanReference(), context.attempt(), context.previousFailureReasons(),
                    context.map().grid().width(), context.map().grid().height(),
                    context.map().obstacles().stream().map(HttpSpatialFeaturePlacementGateway::position).toList(),
                    context.requirements().stream().map(Requirement::from).toList());
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI Game Master spatial feature proposal failed with status " + response.statusCode());
            }
            return parse(mapper.readTree(response.body()));
        } catch (IOException exception) {
            throw new IllegalStateException("AI Game Master spatial feature proposal transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI Game Master spatial feature proposal interrupted", exception);
        }
    }

    private static SpatialFeaturePlacementProposal parse(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("candidates").isArray()) {
            throw new IllegalArgumentException("spatial feature proposal must contain candidates");
        }
        List<SpatialFeaturePlacementProposal.Candidate> candidates = new ArrayList<>();
        for (JsonNode node : root.path("candidates")) {
            List<GridPosition> cells = new ArrayList<>();
            if (node.path("cells").isArray()) for (JsonNode cell : node.path("cells")) {
                cells.add(parsePosition(cell.asText()));
            }
            Set<com.dndmaster.combatmap.domain.SpatialTrigger> triggers = Set.of();
            candidates.add(new SpatialFeaturePlacementProposal.Candidate(
                    UUID.fromString(node.path("featureId").asText()),
                    com.dndmaster.combatmap.domain.SpatialFeatureType.valueOf(node.path("type").asText()), cells,
                    node.path("required").asBoolean(), node.path("evidenceReference").asText(""), null, triggers));
        }
        return new SpatialFeaturePlacementProposal(candidates);
    }

    private static GridPosition parsePosition(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split(",", -1);
        if (parts.length != 2) throw new IllegalArgumentException("spatial feature cell must be x,y");
        try { return new GridPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("spatial feature cell must be x,y", exception); }
    }

    private static String position(GridPosition position) { return position.x() + "," + position.y(); }

    private record Request(String storyPlanReference, int attempt, List<String> previousFailureReasons,
            int gridWidth, int gridHeight, List<String> obstacles, List<Requirement> requirements) {}

    private record Requirement(UUID featureId, String type, boolean required, List<String> evidenceReferences) {
        static Requirement from(SpatialFeaturePreparationInput.Requirement value) {
            return new Requirement(value.featureId(), value.type().name(), value.required(), value.evidenceReferences().stream().toList());
        }
    }
}
