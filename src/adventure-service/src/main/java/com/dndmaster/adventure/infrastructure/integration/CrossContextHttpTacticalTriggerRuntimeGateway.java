package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.TacticalTriggerEvaluator;
import com.dndmaster.adventure.application.runtime.TacticalTriggerRuntimePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal service adapter that applies an already evaluated trigger to Combat Map. */
public final class CrossContextHttpTacticalTriggerRuntimeGateway implements TacticalTriggerRuntimePort {
    private final HttpClient client;
    private final URI baseUrl;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public CrossContextHttpTacticalTriggerRuntimeGateway(HttpClient client, URI baseUrl, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = client; this.baseUrl = baseUrl; this.timeout = timeout; this.mapper = mapper; this.internalToken = internalToken;
    }

    @Override
    public void apply(UUID combatMapId, UUID ownerPlayerId, long expectedVersion, UUID commandId,
            TacticalTriggerEvaluator.Evaluation evaluation) {
        try {
            var body = Map.of("ownerId", ownerPlayerId, "commandId", commandId, "expectedVersion", expectedVersion,
                    "triggerId", evaluation.triggerId(), "kind", evaluation.type(), "targetIds", evaluation.targetIds());
            var request = HttpRequest.newBuilder(baseUrl.resolve("internal/v1/combat-maps/" + combatMapId + "/tactical-triggers"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("combat-map trigger returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("combat-map trigger interrupted", exception);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime : new IllegalStateException("combat-map trigger failed", exception);
        }
    }
}
