package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePlacementModelPort;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Scenario Preparation adapter; Combat Map is never a caller of this gateway. */
public final class HttpScenarioSpatialFeaturePlacementGateway implements ScenarioSpatialFeaturePlacementModelPort {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;
    private final URI mapImageBaseUri;

    public HttpScenarioSpatialFeaturePlacementGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this(client, baseUri, timeout, mapper, internalToken, null);
    }

    public HttpScenarioSpatialFeaturePlacementGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken, URI mapImageBaseUri) {
        this.client = client;
        this.endpoint = baseUri.resolve("internal/v1/gm/spatial-features");
        this.timeout = timeout;
        this.mapper = mapper;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.mapImageBaseUri = mapImageBaseUri;
    }

    @Override
    public ScenarioSpatialFeaturePlacementModelPort.Proposal propose(ScenarioSpatialFeaturePlacementModelPort.Context context) {
        MapDefinition map = context.map();
        String imageDataUri = loadMapImage(map);
        Request payload = new Request(context.soloPlayerId(), map.source().scenarioPackageVersion(), context.attempt(),
                context.previousFailureReasons(), sourceExtent(map, 0), sourceExtent(map, 1), map.obstacles(),
                map.spatialFeatures().stream().map(MapDefinition.SpatialFeatureRequirement::featureId).toList(),
                map.spatialFeatures().stream().map(requirement -> new Requirement(requirement.featureId(), requirement.type(),
                        requirement.required(), requirement.evidenceReferences(), requirement.authoritativeCells())).toList(),
                context.encounters().stream().map(item -> new EncounterRequirement(item.scenarioId(), item.enemyKey(),
                        item.displayName(), item.count(), item.sourceReferences(), item.sourceExcerpts())).toList(),
                map.assetLocator(), imageDataUri);
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("spatial placement proposal failed with status " + response.statusCode());
            }
            return parse(mapper.readTree(response.body()));
        } catch (IOException exception) {
            throw new IllegalStateException("spatial placement proposal transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("spatial placement proposal interrupted", exception);
        }
    }

    private String loadMapImage(MapDefinition map) {
        if (mapImageBaseUri == null || map.source() == null || map.assetLocator().isBlank()) return "";
        String uri = mapImageBaseUri.resolve("internal/v1/combat-map-source-image?sourceDocumentId="
                + map.source().knowledgeDocumentId().value() + "&locator="
                + java.net.URLEncoder.encode(map.assetLocator(), java.nio.charset.StandardCharsets.UTF_8)).toString();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(timeout)
                    .header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return "";
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("map image retrieval failed with status " + response.statusCode());
            var image = mapper.readValue(response.body(), SourceMapImage.class);
            return image.imageDataUri();
        } catch (IOException exception) {
            throw new IllegalStateException("map image retrieval failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("map image retrieval interrupted", exception);
        }
    }

    private static ScenarioSpatialFeaturePlacementModelPort.Proposal parse(JsonNode root) {
        if (root == null || !root.path("candidates").isArray()) throw new IllegalArgumentException("candidates are required");
        List<ScenarioSpatialFeaturePlacementModelPort.Candidate> candidates = new ArrayList<>();
        for (JsonNode node : root.path("candidates")) {
            List<String> cells = new ArrayList<>();
            if (node.path("cells").isArray()) node.path("cells").forEach(cell -> cells.add(cell.asText()));
            candidates.add(new ScenarioSpatialFeaturePlacementModelPort.Candidate(UUID.fromString(node.path("featureId").asText()),
                    node.path("type").asText(), cells, node.path("required").asBoolean()));
        }
        List<ScenarioSpatialFeaturePlacementModelPort.EnemyCandidate> enemies = new ArrayList<>();
        if (root.path("enemies").isArray()) for (JsonNode node : root.path("enemies")) {
            List<ScenarioSpatialFeaturePlacementModelPort.Position> positions = new ArrayList<>();
            if (node.path("positions").isArray()) for (JsonNode position : node.path("positions")) {
                if (!position.path("x").isNumber() || !position.path("y").isNumber()) throw new IllegalArgumentException("enemy position coordinates are required");
                positions.add(new ScenarioSpatialFeaturePlacementModelPort.Position(position.path("x").asDouble(), position.path("y").asDouble()));
            }
            enemies.add(new ScenarioSpatialFeaturePlacementModelPort.EnemyCandidate(node.path("scenarioId").asText(), positions));
        }
        return new ScenarioSpatialFeaturePlacementModelPort.Proposal(candidates, enemies);
    }

    private static int sourceExtent(MapDefinition map, int axis) {
        return java.util.stream.Stream.concat(map.spatialFeatures().stream()
                        .flatMap(feature -> feature.authoritativeCells().stream()), map.obstacles().stream())
                .mapToInt(cell -> Integer.parseInt(cell.split(",", -1)[axis].trim()) + 1)
                .max().orElse(1);
    }

    private record Request(UUID soloPlayerId, @JsonProperty("story" + "PlanReference") String scenarioPackageVersion,
            int attempt, List<String> previousFailureReasons, int gridWidth, int gridHeight, List<String> obstacles,
            List<UUID> requirementIds, List<Requirement> requirements, List<EncounterRequirement> encounters,
            String mapLayoutReference, String imageDataUri) {}

    private record EncounterRequirement(String scenarioId, String enemyKey, String displayName, int count,
            List<String> sourceReferences, List<String> sourceExcerpts) {}
    private record SourceMapImage(String contentType, String imageDataUri) {}

    private record Requirement(UUID featureId, String type, boolean required,
            List<String> evidenceReferences, List<String> authoritativeCells) {}
}
