package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpCombatMapPreparationGatewayTest {
    @Test
    void retries_blocked_map_preparation_on_the_same_draft_with_a_stable_command() throws Exception {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        MapDefinition definition = spatialMapDefinition();
        var context = new CombatMapPreparationPort.ActivationContext(UUID.randomUUID(), UUID.randomUUID(), 1, 1,
                "cellar", "cellar", 1, 2, "the party enters the cellar");
        UUID featureId = definition.spatialFeatures().getFirst().featureId();
        AtomicReference<JsonNode> retryRequest = new AtomicReference<>();
        AtomicReference<UUID> initialCommandId = new AtomicReference<>();
        AtomicReference<UUID> retryCommandId = new AtomicReference<>();
        AtomicInteger retryWriteCount = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            initialCommandId.set(UUID.fromString(body.path("commandId").asText()));
            byte[] response = ("{\"mapId\":\"" + mapId + "\",\"status\":\"BLOCKED\",\"warningCount\":0}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.createContext("/internal/v1/combat-maps/preparation-replay", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            UUID commandId = UUID.fromString(body.path("commandId").asText());
            boolean initial = commandId.equals(initialCommandId.get());
            boolean retried = commandId.equals(retryCommandId.get());
            byte[] response = initial
                    ? ("{\"mapId\":\"" + mapId + "\",\"status\":\"BLOCKED\",\"warningCount\":0}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : retried ? ("{\"mapId\":\"" + mapId + "\",\"status\":\"READY\",\"warningCount\":0}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            exchange.sendResponseHeaders(initial || retried ? 200 : 404, initial || retried ? response.length : -1);
            if (response.length > 0) try (var output = exchange.getResponseBody()) { output.write(response); }
            else exchange.close();
        });
        server.createContext("/internal/v1/adventures/", exchange -> {
            byte[] response = "{\"version\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.createContext("/internal/v1/combat-maps/" + mapId + "/spatial-preparation", exchange -> {
            retryWriteCount.incrementAndGet();
            retryRequest.set(mapper.readTree(exchange.getRequestBody()));
            retryCommandId.set(UUID.fromString(exchange.getRequestHeaders().getFirst("Idempotency-Key")));
            byte[] response = ("{\"mapId\":\"" + mapId + "\",\"status\":\"READY\",\"warningCount\":0}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var gateway = new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), mapper, "secret",
                    new ScenarioSpatialFeaturePreparationService(placementContext -> new ScenarioSpatialFeaturePlacementModelPort.Proposal(
                            List.of(new ScenarioSpatialFeaturePlacementModelPort.Candidate(
                                    featureId, "TRAP", List.of("2,2"), true)))));
            assertThrows(CombatMapPreparationBlockedException.class, () -> gateway.prepareDraft(
                    new AdventureId(adventureId), ownerId, new RuleSetId(ruleSetId), definition, context));

            assertEquals(mapId, gateway.prepareDraft(
                    new AdventureId(adventureId), ownerId, new RuleSetId(ruleSetId), definition, context));
            String identity = "DRAFT|" + adventureId + "|" + definition.id() + "|" + definition.spatialFeatures()
                    + "|null|" + context.situationId() + "|" + context.situationRevision() + "|" + context.entryEvidence();
            assertEquals(UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)), initialCommandId.get());
            UUID expectedRetryId = UUID.nameUUIDFromBytes((identity + "|spatial-retry").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(expectedRetryId, retryCommandId.get());
            assertEquals(expectedRetryId.toString(), retryRequest.get().path("commandId").asText());
            assertEquals(0, retryRequest.get().path("expectedVersion").asInt(-1));
            assertEquals(1, retryRequest.get().path("spatialPlacements").size());
            assertEquals(mapId, gateway.prepareDraft(
                    new AdventureId(adventureId), ownerId, new RuleSetId(ruleSetId), definition, context));
            assertEquals(1, retryWriteCount.get(), "replaying the completed entry must not write features twice");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deactivates_an_old_map_for_a_mapless_situation() throws Exception {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/adventures/" + adventureId + "/combat-map/deactivate", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret");
            gateway.deactivateForMaplessSituation(new AdventureId(adventureId), ownerId);
            assertEquals("POST ownerId=" + ownerId, request.get());
        } finally { server.stop(0); }
    }

    @Test
    void exploration_token_gateway_sends_server_derived_token_preferences_and_reads_map_positions() throws Exception {
        UUID mapId = UUID.randomUUID(), ownerId = UUID.randomUUID(), commandId = UUID.randomUUID(), tokenId = UUID.randomUUID();
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/" + mapId + "/exploration-tokens", exchange -> {
            payload.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] response = ("{\"mapId\":\"" + mapId + "\",\"version\":4,\"tokens\":[{\"id\":\"" + tokenId + "\",\"x\":2,\"y\":3}]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var gateway = new com.dndmaster.adventure.application.combat.HttpCombatMapViewGateway(
                    HttpClient.newHttpClient(), java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret");
            var result = gateway.ensureExplorationTokens(mapId, ownerId, 3, commandId,
                    java.util.Arrays.asList(tokenId, null), tokenId, 2);
            assertEquals(ownerId.toString(), payload.get().path("ownerId").asText());
            assertEquals(tokenId.toString(), payload.get().path("preferredTokenIds").get(0).asText());
            assertTrue(payload.get().path("preferredTokenIds").get(1).isNull());
            assertEquals(2, payload.get().path("count").asInt());
            assertEquals(new com.dndmaster.adventure.application.combat.CombatMapViewPort.ExplorationToken(tokenId, 2, 3), result.getFirst());
        } finally { server.stop(0); }
    }

    @Test
    void player_map_gateway_preserves_public_token_elevation_in_feet() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/adventures/", exchange -> {
            String body = "{\"mapId\":\"" + mapId + "\",\"grid\":{\"width\":2,\"height\":2,\"cellSize\":50,\"distanceUnit\":5},"
                    + "\"tokens\":[{\"id\":\"" + tokenId + "\",\"type\":\"PLAYER\",\"x\":0,\"y\":0,\"elevationFeet\":15}],"
                    + "\"obstacles\":[],\"doors\":[],\"layers\":[],\"current\":[],\"explored\":[],\"version\":3,"
                    + "\"playerStartCandidates\":[],\"spatialFeatures\":[]}";
            byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var gateway = new com.dndmaster.adventure.application.combat.HttpCombatMapViewGateway(
                    HttpClient.newHttpClient(), java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret");
            var token = gateway.playerView(UUID.randomUUID(), UUID.randomUUID()).orElseThrow().tokens().getFirst();
            assertEquals(tokenId, token.id());
            assertEquals(15, token.elevationFeet());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allows_adventure_start_when_no_map_draft_exists() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/adventures/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            UUID adventureId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            var gateway = new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret");

            assertTrue(gateway.mapLayoutConfirmed(new AdventureId(adventureId), ownerId));
            assertEquals("/internal/v1/adventures/" + adventureId + "/combat-map/preparation-view",
                    requestPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void detects_a_reviewed_draft_without_confusing_the_active_map_with_a_draft() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/adventures/", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            byte[] response = "true".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            UUID adventureId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            var gateway = new com.dndmaster.adventure.application.combat.HttpCombatMapViewGateway(
                    HttpClient.newHttpClient(), java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper(), "secret");

            assertTrue(gateway.hasPreparedMap(adventureId, ownerId));
            assertEquals("/internal/v1/adventures/" + adventureId + "/combat-map/prepared-draft?ownerId=" + ownerId,
                    requestPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reports_no_reviewed_draft_when_only_an_active_map_is_available() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/adventures/", exchange -> {
            byte[] response = "false".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var gateway = new com.dndmaster.adventure.application.combat.HttpCombatMapViewGateway(
                    HttpClient.newHttpClient(), java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), Duration.ofSeconds(2), new ObjectMapper(), "secret");
            assertFalse(gateway.hasPreparedMap(UUID.randomUUID(), UUID.randomUUID()));
        } finally {
            server.stop(0);
        }
    }

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
        AtomicReference<String> replayRequestBody = new AtomicReference<>();
        UUID replayedMapId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/preparation-replay", exchange -> {
            replayRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
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
            assertTrue(replayRequestBody.get().contains("\"commandFingerprint\":\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retries_the_same_entry_with_the_same_idempotency_key() throws Exception {
        AtomicReference<String> firstKey = new AtomicReference<>();
        AtomicBoolean sameKey = new AtomicBoolean(true);
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/combat-maps/preparation-replay", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/internal/v1/combat-maps/prepare", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (firstKey.get() == null) firstKey.set(key);
            else sameKey.set(firstKey.get().equals(key));
            byte[] response = ("{\"mapId\":\"" + mapId + "\",\"status\":\"READY\",\"warningCount\":0}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            var gateway = new HttpCombatMapPreparationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret");
            var adventure = new AdventureId(adventureId);
            var rules = new RuleSetId(UUID.randomUUID());
            var definition = mapDefinition();
            var context = new CombatMapPreparationPort.ActivationContext(null, UUID.randomUUID(), 1, 0,
                    "opening", "opening", null, null, "FIRST_NARRATION=entered the hall");

            assertEquals(mapId, gateway.prepareDraft(adventure, ownerId, rules, definition, context));
            assertEquals(mapId, gateway.prepareDraft(adventure, ownerId, rules, definition, context));
            assertTrue(sameKey.get());
            assertTrue(firstKey.get() != null && !firstKey.get().isBlank());
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
                            "rulebook:perception", 15, "PASSIVE", List.of("ENTER_CELL"))),
                    new MapDefinition.MapPosition(1, 2));
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
            assertNull(payload.get("playerSpawnX").isNull() ? null : payload.get("playerSpawnX"));
            assertNull(payload.get("playerSpawnY").isNull() ? null : payload.get("playerSpawnY"));
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
