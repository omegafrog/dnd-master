package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePreparationResult;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePreparationService;
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
import java.nio.charset.StandardCharsets;

/** HTTP adapter for the combat-map prepare-and-activate boundary. */
public final class HttpCombatMapPreparationGateway implements CombatMapPreparationPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;
    private final ScenarioSpatialFeaturePreparationService spatialPreparation;

    public HttpCombatMapPreparationGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this(client, baseUri, timeout, mapper, internalToken, null);
    }

    public HttpCombatMapPreparationGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken, ScenarioSpatialFeaturePreparationService spatialPreparation) {
        this.client = client;
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.mapper = mapper;
        this.internalToken = internalToken;
        this.spatialPreparation = spatialPreparation;
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
        return sendPrepare(adventureId, ownerPlayerId, ruleSetId, mapDefinition, stagePosition, context);
    }

    @Override
    public UUID prepareDraft(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, CombatMapPreparationPort.ActivationContext context) {
        return sendPrepare(adventureId, ownerPlayerId, ruleSetId, mapDefinition, null, context);
    }

    @Override
    public UUID activatePrepared(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            int stagePosition, CombatMapPreparationPort.ActivationContext context) {
        return sendPrepare(adventureId, ownerPlayerId, ruleSetId, null, stagePosition, context);
    }

    private UUID sendPrepare(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, Integer stagePosition, CombatMapPreparationPort.ActivationContext context) {
        ScenarioSpatialFeaturePreparationResult spatial = prepareSpatialFeatures(mapDefinition);
        Request payload = new Request(adventureId.value(), ownerPlayerId, ruleSetId.value(), mapDefinition == null ? null : mapDefinition.id(),
                mapDefinition == null ? null : mapDefinition.assetId(), mapDefinition == null ? null : mapDefinition.assetLocator(), stagePosition,
                context.placementProposalX(), context.placementProposalY(), context.playerTokenId(), context.situationId(),
                context.situationRevision(), context.turnIndex(), context.currentScene(), context.location(), context.entryEvidence(),
                mapDefinition == null ? List.of() : mapDefinition.walls(), mapDefinition == null ? List.of() : mapDefinition.doors(), mapDefinition == null ? List.of() : mapDefinition.obstacles(),
                mapDefinition == null || mapDefinition.source() == null ? null : mapDefinition.source().knowledgeDocumentId().value(),
                mapDefinition == null || mapDefinition.source() == null ? null : mapDefinition.source().locator(),
                mapDefinition == null || mapDefinition.source() == null ? "story-plan:unknown" : mapDefinition.source().scenarioPackageVersion(),
                spatial.placements(), spatial.activationAllowed() ? false : true, spatial.warnings(), spatial.failures(), null, 0, "unassigned");
        try {
            String identity = phase(mapDefinition, stagePosition) + "|"
                    + adventureId.value() + "|" + (mapDefinition == null ? "prepared"
                            : mapDefinition.id() + "|" + mapDefinition.spatialFeatures()) + "|"
                    + stagePosition + "|" + context.situationId() + "|" + context.situationRevision() + "|"
                    + context.entryEvidence();
            UUID commandId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            long expectedVersion = mapDefinition == null ? preparationVersion(adventureId, ownerPlayerId) : 0;
            String fingerprint = identity;
            payload = payload.withCommand(commandId, expectedVersion, fingerprint);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/prepare"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("Idempotency-Key", commandId.toString())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.body() != null && response.body().contains("MAP_SPAWN_REVIEW_REQUIRED")) {
                    throw new CombatMapPlacementRequiredException();
                }
                throw new IllegalStateException("combat map preparation failed with status " + response.statusCode());
            }
            Response result = mapper.readValue(response.body(), Response.class);
            if (result.status() == Status.BLOCKED) throw new CombatMapPreparationBlockedException();
            return result.mapId();
        } catch (IOException exception) {
            throw new IllegalStateException("combat map preparation transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("combat map preparation interrupted", exception);
        }
    }

    private ScenarioSpatialFeaturePreparationResult prepareSpatialFeatures(MapDefinition mapDefinition) {
        if (mapDefinition == null || mapDefinition.spatialFeatures().isEmpty()) {
            return new ScenarioSpatialFeaturePreparationResult(true, List.of(), List.of(), List.of(), 0);
        }
        if (spatialPreparation == null) {
            throw new IllegalStateException("spatial feature preparation service is required for maps with spatial features");
        }
        return spatialPreparation.prepare(mapDefinition);
    }

    private long preparationVersion(AdventureId adventureId, UUID ownerPlayerId) {
        URI uri = baseUri.resolve("internal/v1/adventures/" + adventureId.value()
                + "/combat-map/preparation-view?ownerId=" + ownerPlayerId);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("combat map preparation version lookup failed with status " + response.statusCode());
            }
            long version = mapper.readTree(response.body()).path("version").asLong(-1);
            if (version < 0) throw new IllegalStateException("combat map preparation version is missing");
            return version;
        } catch (IOException exception) {
            throw new IllegalStateException("combat map preparation version lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("combat map preparation version lookup interrupted", exception);
        }
    }

    private static String phase(MapDefinition mapDefinition, Integer stagePosition) {
        return mapDefinition == null ? "ACTIVATE" : stagePosition == null ? "DRAFT" : "INITIAL";
    }

    private record Request(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId,
            String assetId, String assetLocator, Integer stagePosition, Integer playerSpawnX, Integer playerSpawnY,
            UUID playerTokenId, UUID situationId, long situationRevision, int turnIndex,
            String currentScene, String location, String entryEvidence,
            List<String> walls, List<String> doors, List<String> obstacles,
            UUID sourceDocumentId, String sourceAssetLocator, String spatialPreparationReference,
            List<ScenarioSpatialFeaturePreparationResult.Placement> spatialPlacements, boolean spatialPreparationBlocked,
            List<String> spatialWarnings, List<String> spatialFailures,
            UUID commandId, long expectedVersion, String operationFingerprint) {
        Request withCommand(UUID commandId, long expectedVersion, String operationFingerprint) {
            return new Request(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, stagePosition,
                    playerSpawnX, playerSpawnY, playerTokenId, situationId, situationRevision, turnIndex, currentScene,
                    location, entryEvidence, walls, doors, obstacles, sourceDocumentId, sourceAssetLocator,
                    spatialPreparationReference, spatialPlacements, spatialPreparationBlocked, spatialWarnings, spatialFailures,
                    commandId, expectedVersion, operationFingerprint);
        }
    }
    private enum Status { READY, BLOCKED }
    private record Response(UUID mapId, Status status, int warningCount) {
        Response(UUID mapId) { this(mapId, Status.READY, 0); }
    }
}
