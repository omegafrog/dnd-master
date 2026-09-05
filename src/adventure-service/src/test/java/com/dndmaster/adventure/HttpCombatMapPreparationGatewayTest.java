package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.combat.HttpCombatMapPreparationGateway;
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
            assertTrue(payload.get("entrySide").isNull());
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
}
