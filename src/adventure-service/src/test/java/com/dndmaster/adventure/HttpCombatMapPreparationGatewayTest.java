package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.combat.CombatMapPreparationBlockedException;
import com.dndmaster.adventure.application.combat.HttpCombatMapPreparationGateway;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePlacementModelPort;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePreparationService;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapDefinition.MapGrid;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpCombatMapPreparationGatewayTest {
    @Test
    void sends_runtime_activation_context_without_synthetic_zero_spawn() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"mapId\":\"" + UUID.randomUUID() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            UUID playerTokenId = UUID.randomUUID();
            UUID situationId = UUID.randomUUID();
            new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret")
                    .prepareInitial(new AdventureId(UUID.randomUUID()), UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                            mapDefinition(), 1,
                            new CombatMapPreparationPort.ActivationContext(playerTokenId, situationId, 3, 7,
                                    "opening", "north gate", null, null, null));

            JsonNode payload = new ObjectMapper().readTree(requestBody.get());
            assertNull(payload.get("playerSpawnX").isNull() ? null : payload.get("playerSpawnX"));
            assertNull(payload.get("playerSpawnY").isNull() ? null : payload.get("playerSpawnY"));
            assertEquals(playerTokenId.toString(), payload.get("playerTokenId").asText());
            assertEquals(situationId.toString(), payload.get("situationId").asText());
            assertEquals(3, payload.get("situationRevision").asInt());
            assertEquals(7, payload.get("turnIndex").asInt());
            assertTrue(payload.get("entryEvidence").isNull() || payload.get("entryEvidence").asText().isBlank());
            assertTrue(payload.hasNonNull("sourceDocumentId"));
            assertEquals("page-1", payload.get("sourceAssetLocator").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void activates_only_an_existing_reviewed_draft_without_sending_a_new_map_definition() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        UUID adventureId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"mapId\":\"" + UUID.randomUUID() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.createContext("/internal/v1/adventures/" + adventureId + "/combat-map/preparation-view", exchange -> {
            byte[] response = "{\"version\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            UUID ownerId = UUID.randomUUID();
            UUID rulesId = UUID.randomUUID();
            UUID situationId = UUID.randomUUID();
            UUID playerTokenId = UUID.randomUUID();
            UUID mapId = new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret")
                    .activatePrepared(new AdventureId(adventureId), ownerId, new RuleSetId(rulesId), 1,
                            new CombatMapPreparationPort.ActivationContext(playerTokenId, situationId, 4, 8,
                                    "cellar-combat", "basement", null, null, "FIRST_NARRATION=entered the basement"));

            JsonNode payload = new ObjectMapper().readTree(requestBody.get());
            assertEquals(1, payload.get("stagePosition").asInt());
            assertTrue(payload.get("mapDefinitionId").isNull());
            assertTrue(payload.get("assetId").isNull());
            assertEquals(adventureId.toString(), payload.get("adventureId").asText());
            assertEquals(ownerId.toString(), payload.get("ownerId").asText());
            assertEquals(playerTokenId.toString(), payload.get("playerTokenId").asText());
            assertEquals("FIRST_NARRATION=entered the basement", payload.get("entryEvidence").asText());
            assertTrue(mapId != null);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replays_before_requiring_a_second_spatial_model_proposal() throws Exception {
        AtomicBoolean prepareCalled = new AtomicBoolean();
        UUID replayedMapId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/preparation-replay", exchange -> {
            byte[] response = ("{\"mapId\":\"" + replayedMapId + "\",\"status\":\"READY\",\"warningCount\":0}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            prepareCalled.set(true);
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            UUID result = new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret")
                    .prepareInitial(new AdventureId(UUID.randomUUID()), UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                            spatialMapDefinition(), 1);
            assertEquals(replayedMapId, result);
            assertTrue(!prepareCalled.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sends_authoritative_spatial_requirements_to_combat_map_preparation() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"mapId\":\"" + UUID.randomUUID() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            UUID featureId = UUID.randomUUID();
            UUID documentId = UUID.randomUUID();
            MapDefinition definition = new MapDefinition(UUID.randomUUID(), "map", "page-1",
                    new MapGrid(0, 0, 50, 0, "5 ft"), List.of(), List.of(), List.of(),
                    new MapSourceReference(new KnowledgeDocumentId(documentId), 4, "page-1", "9"),
                    .9, MapSafetyStatus.SAFE,
                    List.of(new MapDefinition.SpatialFeatureRequirement(featureId, "TRAP", true,
                            List.of("storybook:page-4"), List.of("2,2"), "resolution-unit-1",
                            "rulebook:perception", 15, "PASSIVE", List.of("ENTER_CELL"))));
            new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret",
                    new ScenarioSpatialFeaturePreparationService(context -> new ScenarioSpatialFeaturePlacementModelPort.Proposal(List.of(
                            new ScenarioSpatialFeaturePlacementModelPort.Candidate(featureId, "TRAP", List.of("2,2"), true)))))
                    .prepareInitial(new AdventureId(UUID.randomUUID()), UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                            definition, 1);

            JsonNode payload = new ObjectMapper().readTree(requestBody.get());
            assertEquals("TRAP", payload.get("spatialPlacements").get(0).get("type").asText());
            assertEquals(featureId.toString(), payload.get("spatialPlacements").get(0).get("featureId").asText());
            assertEquals(documentId.toString(), payload.get("spatialPlacements").get(0).get("evidence").get("sourceDocumentId").asText());
            assertEquals("2,2", payload.get("spatialPlacements").get(0).get("evidence").get("allowedCells").get(0).asText());
            assertEquals("9", payload.get("spatialPreparationReference").asText());
            assertEquals("resolution-unit-1", payload.get("spatialPlacements").get(0).get("evidence").get("resolutionUnitId").asText());
            assertEquals("9", payload.get("spatialPlacements").get(0).get("evidence").get("scenarioPackageVersion").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maps_required_preparation_failure_to_a_safe_typed_result() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            byte[] response = ("{\"mapId\":\"" + UUID.randomUUID()
                    + "\",\"status\":\"BLOCKED\",\"warningCount\":0}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var exception = org.junit.jupiter.api.Assertions.assertThrows(CombatMapPreparationBlockedException.class,
                    () -> new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                            java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                            Duration.ofSeconds(2), new ObjectMapper(), "secret")
                            .prepareInitial(new AdventureId(UUID.randomUUID()), UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                                    mapDefinition(), 1));
            assertEquals("combat map preparation is blocked", exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private static MapDefinition mapDefinition() {
        UUID documentId = UUID.randomUUID();
        return new MapDefinition(UUID.randomUUID(), "map", "page-1", new MapGrid(0, 0, 50, 0, "5 ft"),
                List.of(), List.of(), List.of(), new MapSourceReference(new KnowledgeDocumentId(documentId), 1, "page-1"),
                .9, MapSafetyStatus.SAFE);
    }

    private static MapDefinition spatialMapDefinition() {
        UUID featureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        return new MapDefinition(UUID.randomUUID(), "map", "page-1", new MapGrid(0, 0, 50, 0, "5 ft"),
                List.of(), List.of(), List.of(), new MapSourceReference(new KnowledgeDocumentId(documentId), 1, "page-1", "9"),
                .9, MapSafetyStatus.SAFE,
                List.of(new MapDefinition.SpatialFeatureRequirement(featureId, "TRAP", true,
                        List.of("source:page-1"), List.of("2,2"), "resolution-unit-1",
                        "rulebook:perception", 15, "PASSIVE", List.of("ENTER_CELL"))));
    }
}
