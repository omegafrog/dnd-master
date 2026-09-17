package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.application.spatial.HttpSpatialFeaturePlacementGateway;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePlacementContext;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePreparationInput;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.DetectionSpec;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpSpatialFeaturePlacementGatewayTest {
    @Test
    void sends_authoritative_requirements_and_returns_only_ai_candidates() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        UUID featureId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/gm/spatial-features", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"candidates\":[{\"featureId\":\"" + featureId
                    + "\",\"type\":\"TRAP\",\"cells\":[\"2,3\"],\"required\":true,"
                    + "\"evidenceReference\":\"document:source:7:asset:map-1\"}]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            SpatialFeaturePreparationInput.Requirement requirement = new SpatialFeaturePreparationInput.Requirement(
                    featureId, SpatialFeatureType.TRAP, true, Set.of("document:source:7:asset:map-1"),
                    DetectionSpec.passive("rulebook:perception", 15), Set.of(SpatialTrigger.ENTER_CELL));
            CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                    new RuleSetId(UUID.randomUUID()), new GridSpec(8, 8, 50, 5), List.of(), Set.of(), List.of());
            var proposal = new HttpSpatialFeaturePlacementGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), new ObjectMapper(), "secret")
                    .propose(new SpatialFeaturePlacementContext(map, "story-plan:source", 1, List.of(), List.of(requirement)));

            JsonNode request = new ObjectMapper().readTree(requestBody.get());
            assertEquals("story-plan:source", request.path("storyPlanReference").asText());
            assertEquals(featureId.toString(), request.path("requirements").get(0).path("featureId").asText());
            assertEquals("document:source:7:asset:map-1",
                    request.path("requirements").get(0).path("evidenceReferences").get(0).asText());
            assertEquals(featureId, proposal.candidates().getFirst().featureId());
            assertEquals(new GridPosition(2, 3), proposal.candidates().getFirst().cells().getFirst());
        } finally {
            server.stop(0);
        }
    }
}
