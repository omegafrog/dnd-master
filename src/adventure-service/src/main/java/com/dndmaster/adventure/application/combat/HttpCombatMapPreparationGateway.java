package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/** HTTP adapter for the combat-map prepare-and-activate boundary. */
public final class HttpCombatMapPreparationGateway implements CombatMapPreparationPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpCombatMapPreparationGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client;
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.mapper = mapper;
        this.internalToken = internalToken;
    }

    @Override
    public UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, int stagePosition) {
        return prepareInitial(adventureId, ownerPlayerId, ruleSetId, mapDefinition, stagePosition,
                new CombatMapPreparationPort.ActivationContext(null, UUID.randomUUID(), 1, 0,
                        "unknown", "unknown", null, null, null));
    }

    @Override
    public boolean mapLayoutConfirmed(AdventureId adventureId, UUID ownerPlayerId) {
        URI preparationUri = baseUri.resolve("internal/v1/adventures/" + adventureId.value()
                + "/combat-map/preparation-view?ownerId=" + ownerPlayerId);
        try {
            HttpRequest preparation = HttpRequest.newBuilder(preparationUri)
                    .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(preparation, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return true;
            if (response.statusCode() < 200 || response.statusCode() >= 300) return false;
            JsonNode body = mapper.readTree(response.body());
            JsonNode layers = body.path("layers");
            if (!layers.isArray()) return false;
            JsonNode marker = null;
            for (JsonNode layer : layers) {
                if ("MAP_LAYOUT_CONFIRMED".equals(layer.path("type").asText())) { marker = layer; break; }
            }
            if (marker == null) return false;
            String value = marker.path("value").asText("");
            String prefix = "USER|ALIGNMENT_VERSION=";
            if (!value.startsWith(prefix)) return false;
            long savedVersion;
            try { savedVersion = Long.parseLong(value.substring(prefix.length())); }
            catch (NumberFormatException ignored) { return false; }
            String mapId = body.path("mapId").asText("");
            if (mapId.isBlank()) return false;
            URI alignmentUri = baseUri.resolve("internal/v1/combat-maps/" + mapId + "/alignment?ownerId=" + ownerPlayerId);
            HttpRequest alignment = HttpRequest.newBuilder(alignmentUri)
                    .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> alignmentResponse = client.send(alignment, HttpResponse.BodyHandlers.ofString());
            if (alignmentResponse.statusCode() < 200 || alignmentResponse.statusCode() >= 300) return false;
            return mapper.readTree(alignmentResponse.body()).path("version").asLong(-1) == savedVersion;
        } catch (IOException exception) {
            throw new IllegalStateException("combat map preparation transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("combat map preparation interrupted", exception);
        }
    }

    @Override
    public UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, int stagePosition, CombatMapPreparationPort.ActivationContext context) {
        Request payload = new Request(adventureId.value(), ownerPlayerId, ruleSetId.value(), mapDefinition.id(),
                mapDefinition.assetId(), mapDefinition.assetLocator(), stagePosition,
                context.spawnCandidateX(), context.spawnCandidateY(), context.playerTokenId(), context.situationId(),
                context.situationRevision(), context.turnIndex(), context.currentScene(), context.location(), context.entrySide(),
                mapDefinition.walls(), mapDefinition.doors(), mapDefinition.obstacles(),
                mapDefinition.source().knowledgeDocumentId().value(), mapDefinition.source().locator());
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/prepare"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("combat map preparation failed with status " + response.statusCode());
            }
            return mapper.readValue(response.body(), Response.class).mapId();
        } catch (IOException exception) {
            throw new IllegalStateException("combat map preparation transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("combat map preparation interrupted", exception);
        }
    }

    private record Request(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId,
            String assetId, String assetLocator, int stagePosition, Integer playerSpawnX, Integer playerSpawnY,
            UUID playerTokenId, UUID situationId, long situationRevision, int turnIndex,
            String currentScene, String location, String entrySide,
            List<String> walls, List<String> doors, List<String> obstacles,
            UUID sourceDocumentId, String sourceAssetLocator) {}
    private record Response(UUID mapId) {}
}
