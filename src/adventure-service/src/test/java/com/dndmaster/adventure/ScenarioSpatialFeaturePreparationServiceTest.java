package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePlacementModelPort;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePreparationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioSpatialFeaturePreparationServiceTest {
    @Test
    void spatial_placement_http_request_carries_owner_and_source_cell_bounds() throws Exception {
        UUID owner = UUID.randomUUID();
        MapDefinition map = map(UUID.randomUUID(), true, "TRAP", List.of("4,3"), -1, "", false);
        var body = new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/internal/v1/gm/spatial-features", exchange -> {
            body.set(new com.fasterxml.jackson.databind.ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] response = "{\"candidates\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var gateway = new com.dndmaster.adventure.infrastructure.integration.HttpScenarioSpatialFeaturePlacementGateway(
                    java.net.http.HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    java.time.Duration.ofSeconds(2), new com.fasterxml.jackson.databind.ObjectMapper(), "token");
            gateway.propose(new ScenarioSpatialFeaturePlacementModelPort.Context(owner, map, 1, List.of()));
            assertEquals(owner.toString(), body.get().path("soloPlayerId").asText());
            assertEquals(5, body.get().path("gridWidth").asInt());
            assertEquals(4, body.get().path("gridHeight").asInt());
            assertTrue(body.get().path("obstacles").isArray());
            assertEquals("4,3", body.get().path("requirements").get(0).path("authoritativeCells").get(0).asText());
            assertEquals(map.assetLocator(), body.get().path("mapLayoutReference").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sends_connected_player_with_locked_feature_to_the_placement_model() {
        UUID owner = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        MapDefinition map = map(featureId, true, "TRAP", List.of("4,3"), -1, "", false);
        var seen = new java.util.concurrent.atomic.AtomicReference<ScenarioSpatialFeaturePlacementModelPort.Context>();
        var result = new ScenarioSpatialFeaturePreparationService(context -> {
            seen.set(context);
            return new ScenarioSpatialFeaturePlacementModelPort.Proposal(List.of(
                    new ScenarioSpatialFeaturePlacementModelPort.Candidate(featureId, "TRAP", List.of("4,3"), true)));
        }).prepare(map, owner);
        assertTrue(result.activationAllowed());
        assertEquals(owner, seen.get().soloPlayerId());
        assertEquals(map.id(), seen.get().map().id());
    }

    @Test
    void sends_only_validated_structured_batch_facts_to_the_map_boundary() {
        UUID featureId = UUID.randomUUID();
        MapDefinition map = map(featureId, true, "MAGICAL_AREA_EFFECT", List.of("2,2"), 3, "EXPIRE", true);

        var result = new ScenarioSpatialFeaturePreparationService(context ->
                new ScenarioSpatialFeaturePlacementModelPort.Proposal(List.of(
                        new ScenarioSpatialFeaturePlacementModelPort.Candidate(featureId, "MAGICAL_AREA_EFFECT", List.of("2,2"), true))))
                .prepare(map, UUID.randomUUID());

        assertTrue(result.activationAllowed());
        assertEquals(1, result.placements().size());
        var placement = result.placements().getFirst();
        assertEquals(map.source().knowledgeDocumentId().value(), placement.evidence().sourceDocumentId());
        assertEquals("resolution-unit-1", placement.evidence().resolutionUnitId());
        assertEquals("4", placement.evidence().scenarioPackageVersion());
        assertEquals(List.of("2,2"), placement.evidence().allowedCells());
        assertEquals(3, placement.durationTurns());
        assertEquals("EXPIRE", placement.removalPolicy());
        assertTrue(placement.overlapAllowed());
    }

    @Test
    void unsupported_coordinates_block_after_three_attempts_without_returning_hidden_candidates() {
        UUID featureId = UUID.randomUUID();
        MapDefinition map = map(featureId, true, "TRAP", List.of("2,2"), -1, "", false);

        var result = new ScenarioSpatialFeaturePreparationService(context ->
                new ScenarioSpatialFeaturePlacementModelPort.Proposal(List.of(
                        new ScenarioSpatialFeaturePlacementModelPort.Candidate(featureId, "TRAP", List.of("9,9"), true))))
                .prepare(map, UUID.randomUUID());

        assertFalse(result.activationAllowed());
        assertEquals(3, result.attempts());
        assertTrue(result.placements().isEmpty());
        assertTrue(result.failures().stream().allMatch(message -> !message.contains("9,9")));
    }

    private static MapDefinition map(UUID featureId, boolean required, String type, List<String> cells,
            int duration, String removal, boolean overlap) {
        return new MapDefinition(UUID.randomUUID(), "map", "page-1",
                new MapDefinition.MapGrid(0, 0, 50, 0, "5 ft"), List.of(), List.of(), List.of(),
                new MapSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 7, "asset:map-1", "4"),
                .95, MapSafetyStatus.SAFE,
                List.of(new MapDefinition.SpatialFeatureRequirement(featureId, type, required,
                        List.of("source-section:trap"), cells, "resolution-unit-1", "rulebook:perception", 15, "PASSIVE",
                        List.of("ENTER_CELL"), duration, removal, overlap, true)));
    }
}
