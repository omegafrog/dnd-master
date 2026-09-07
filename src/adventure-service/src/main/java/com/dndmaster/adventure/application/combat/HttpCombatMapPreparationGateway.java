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
    public UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, int stagePosition, CombatMapPreparationPort.ActivationContext context) {
        Request payload = new Request(adventureId.value(), ownerPlayerId, ruleSetId.value(), mapDefinition.id(),
                mapDefinition.assetId(), mapDefinition.assetLocator(), stagePosition,
                context.spawnCandidateX(), context.spawnCandidateY(), context.playerTokenId(), context.situationId(),
                context.situationRevision(), context.turnIndex(), context.currentScene(), context.location(), context.entrySide());
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
            String currentScene, String location, String entrySide) {}
    private record Response(UUID mapId) {}
}
