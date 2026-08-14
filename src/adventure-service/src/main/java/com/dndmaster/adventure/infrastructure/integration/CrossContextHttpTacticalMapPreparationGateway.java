package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.TacticalMapPreparationPort;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
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
        try {
            Map<String, Object> body = Map.of(
                    "adventureId", adventureId, "ownerId", ownerPlayerId,
                    "ruleSetId", ruleSetId,
                    "mapDefinitionId", definition.id(), "assetId", definition.assetId(),
                    "assetLocator", definition.assetLocator(), "playerSpawnX", playerSpawnX, "playerSpawnY", playerSpawnY);
            HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("internal/v1/combat-maps/prepare"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat-map prepare returned HTTP " + response.statusCode());
            return UUID.fromString(mapper.readTree(response.body()).path("mapId").asText());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("combat-map prepare interrupted", e); }
          catch (Exception e) { throw new IllegalStateException("combat-map prepare failed", e); }
    }
}
