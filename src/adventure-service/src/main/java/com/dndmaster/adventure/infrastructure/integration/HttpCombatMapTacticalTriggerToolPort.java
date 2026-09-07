package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GmToolInvocation;
import com.dndmaster.adventure.application.runtime.GmToolOutcome;
import com.dndmaster.adventure.application.runtime.OfficialToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Official GM tool adapter for planned Combat Map tactical triggers. */
public final class HttpCombatMapTacticalTriggerToolPort implements OfficialToolPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String token;

    public HttpCombatMapTacticalTriggerToolPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String token) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.token = token == null ? "" : token;
    }

    @Override
    public GmToolOutcome execute(GmToolInvocation invocation) {
        try {
            if (invocation.executionContext() == null) return GmToolOutcome.rejected("combat map tool execution context missing");
            ObjectNode body = (ObjectNode) mapper.readTree(invocation.argumentsJson());
            String mapId = body.path("mapId").asText();
            if (mapId.isBlank()) throw new IllegalArgumentException("mapId required");
            long expectedVersion = body.path("expectedVersion").asLong(-1);
            if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion required");
            if (body.path("qualifyingAction").asText().isBlank()) throw new IllegalArgumentException("qualifyingAction required");
            body.put("ownerId", invocation.ownerPlayerId().toString());
            body.put("commandId", invocation.invocationId().toString());
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/combat-maps/" + java.util.UUID.fromString(mapId) + "/tactical-triggers"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", token)
                    .header("X-Session-ID", invocation.sessionId().toString())
                    .header("Idempotency-Key", invocation.invocationId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return GmToolOutcome.rejected("combat map rejected tactical trigger: " + response.statusCode());
            return GmToolOutcome.completed(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return GmToolOutcome.rejected("combat map trigger interrupted");
        } catch (IllegalArgumentException exception) {
            return GmToolOutcome.rejected("combat map trigger arguments invalid");
        } catch (Exception exception) {
            return GmToolOutcome.unknown("combat map trigger outcome unknown");
        }
    }
}
