package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.TacticalMapPreparationPort;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Materializes the plan-selected, source-pinned map in combat-map service. */
public final class CrossContextHttpTacticalMapPreparationGateway implements TacticalMapPreparationPort {
    private final HttpClient client;
    private final URI baseUrl;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public CrossContextHttpTacticalMapPreparationGateway(HttpClient client, URI baseUrl, Duration timeout, ObjectMapper mapper) {
        this.client = client; this.baseUrl = baseUrl; this.timeout = timeout; this.mapper = mapper;
    }

    @Override
    public UUID prepare(UUID adventureId, UUID ownerPlayerId, MapDefinition definition) {
        return prepare(adventureId, ownerPlayerId, UUID.nameUUIDFromBytes("dnd-5e-2014".getBytes()), definition);
    }

    @Override
    public UUID prepare(UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, MapDefinition definition) {
        return prepare(adventureId, ownerPlayerId, ruleSetId, definition, 0, 0);
    }

    @Override
    public UUID prepare(UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, MapDefinition definition, int playerSpawnX, int playerSpawnY) {
        return prepare(adventureId, ownerPlayerId, ruleSetId, definition, null, playerSpawnX, playerSpawnY);
    }

    @Override
    public UUID prepare(UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, MapDefinition definition, TacticalScenePlan scene,
            int playerSpawnX, int playerSpawnY) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                    "adventureId", adventureId, "ownerId", ownerPlayerId,
                    "ruleSetId", ruleSetId,
                    "mapDefinitionId", definition.id(), "assetId", definition.assetId(), "assetLocator", definition.assetLocator(),
                    "playerSpawnX", playerSpawnX, "playerSpawnY", playerSpawnY));
            if (scene != null) body.put("tacticalScene", tacticalScene(scene));
            HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("internal/v1/combat-maps/prepare"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat-map prepare returned HTTP " + response.statusCode());
            return UUID.fromString(mapper.readTree(response.body()).path("mapId").asText());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat-map prepare interrupted", e); }
          catch (Exception e) { throw new IllegalStateException("combat-map prepare failed", e); }
    }

    private static Map<String, Object> tacticalScene(TacticalScenePlan scene) {
        java.util.List<Map<String, Object>> placements = new java.util.ArrayList<>();
        addPlacements(placements, scene.players(), "PLAYER"); addPlacements(placements, scene.allies(), "FRIENDLY_NPC");
        addPlacements(placements, scene.npcs(), "NPC"); addPlacements(placements, scene.enemies(), "ENEMY");
        addPlacements(placements, scene.bosses(), "BOSS"); addPlacements(placements, scene.interactiveObjects(), "OBJECT");
        var environments = scene.environments().stream().map(item -> Map.<String, Object>of("id", item.id(), "kind", item.kind(),
                "x", item.coordinate().x(), "y", item.coordinate().y())).toList();
        var hiddenRegions = scene.initialFog().hiddenRegions().stream()
                .map(item -> Map.<String, Object>of("x", item.x(), "y", item.y())).toList();
        return Map.of("placements", placements, "environments", environments, "hiddenRegions", hiddenRegions);
    }

    private static void addPlacements(java.util.List<Map<String, Object>> result,
            java.util.List<com.dndmaster.adventure.domain.adventure.TacticalPlacement> placements, String kind) {
        placements.forEach(item -> result.add(Map.of("id", item.id(), "kind", kind,
                "x", item.coordinate().x(), "y", item.coordinate().y())));
    }
}
