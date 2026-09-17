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

    public HttpScenarioSpatialFeaturePlacementGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client;
        this.endpoint = baseUri.resolve("internal/v1/gm/spatial-features");
        this.timeout = timeout;
        this.mapper = mapper;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    public ScenarioSpatialFeaturePlacementModelPort.Proposal propose(ScenarioSpatialFeaturePlacementModelPort.Context context) {
        MapDefinition map = context.map();
        Request payload = new Request(map.source().scenarioPackageVersion(), context.attempt(),
                context.previousFailureReasons(), map.spatialFeatures().stream().map(MapDefinition.SpatialFeatureRequirement::featureId).toList(),
                map.spatialFeatures().stream().map(requirement -> new Requirement(requirement.featureId(), requirement.type(),
                        requirement.required(), requirement.evidenceReferences(), requirement.authoritativeCells())).toList());
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

    private static ScenarioSpatialFeaturePlacementModelPort.Proposal parse(JsonNode root) {
        if (root == null || !root.path("candidates").isArray()) throw new IllegalArgumentException("candidates are required");
        List<ScenarioSpatialFeaturePlacementModelPort.Candidate> candidates = new ArrayList<>();
        for (JsonNode node : root.path("candidates")) {
            List<String> cells = new ArrayList<>();
            if (node.path("cells").isArray()) node.path("cells").forEach(cell -> cells.add(cell.asText()));
            candidates.add(new ScenarioSpatialFeaturePlacementModelPort.Candidate(UUID.fromString(node.path("featureId").asText()),
                    node.path("type").asText(), cells, node.path("required").asBoolean()));
        }
        return new ScenarioSpatialFeaturePlacementModelPort.Proposal(candidates);
    }

    private record Request(@JsonProperty("story" + "PlanReference") String scenarioPackageVersion, int attempt, List<String> previousFailureReasons,
            List<UUID> requirementIds, List<Requirement> requirements) {}

    private record Requirement(UUID featureId, String type, boolean required,
            List<String> evidenceReferences, List<String> authoritativeCells) {}
}
