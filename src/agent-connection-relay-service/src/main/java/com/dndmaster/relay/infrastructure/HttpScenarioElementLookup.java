package com.dndmaster.relay.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Reads exact element candidates from the active adventure's locked scenario package. */
public final class HttpScenarioElementLookup {
    private final HttpClient client;
    private final URI baseUri;
    private final URI endpoint;
    private final String token;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public HttpScenarioElementLookup(HttpClient client, URI baseUri, Duration timeout, String token, ObjectMapper mapper) {
        this.client = client;
        this.baseUri = baseUri;
        this.endpoint = baseUri.resolve("internal/v1/adventures/scenario-elements/search");
        this.timeout = timeout;
        this.token = token == null ? "" : token;
        this.mapper = mapper;
    }

    public JsonNode search(UUID authenticatedOwner, JsonNode confirmedScope, String query) throws Exception {
        if (confirmedScope == null || !confirmedScope.isObject()
                || !authenticatedOwner.toString().equals(confirmedScope.path("ownerId").asText())) {
            throw new SecurityException("scenario lookup scope owner mismatch");
        }
        String sessionId = confirmedScope.path("sessionId").asText("");
        String scenarioPackageId = confirmedScope.path("scenarioPackageId").asText("");
        if (sessionId.isBlank() || scenarioPackageId.isBlank()) throw new IllegalArgumentException("scenario lookup scope is incomplete");
        if (query == null || query.isBlank() || query.length() > 1000) throw new IllegalArgumentException("scenario query must contain 1 to 1000 characters");
        var payload = mapper.createObjectNode().put("ownerId", authenticatedOwner.toString())
                .put("sessionId", sessionId).put("scenarioPackageId", scenarioPackageId).put("query", query.trim());
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("X-Internal-Token", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("scenario lookup failed with status " + response.statusCode());
        if (response.body().length > 1_000_000) throw new IllegalStateException("scenario lookup response exceeds the size limit");
        return mapper.readTree(response.body());
    }

    public JsonNode availableSituations(UUID authenticatedOwner, JsonNode confirmedScope) throws Exception {
        if (confirmedScope == null || !confirmedScope.isObject()
                || !authenticatedOwner.toString().equals(confirmedScope.path("ownerId").asText()))
            throw new SecurityException("situation lookup scope owner mismatch");
        String sessionId = confirmedScope.path("sessionId").asText("");
        String scenarioPackageId = confirmedScope.path("scenarioPackageId").asText("");
        if (sessionId.isBlank() || scenarioPackageId.isBlank())
            throw new IllegalArgumentException("situation lookup scope is incomplete");
        var payload = mapper.createObjectNode().put("ownerId", authenticatedOwner.toString())
                .put("sessionId", sessionId).put("scenarioPackageId", scenarioPackageId);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/adventures/situations/available"))
                .timeout(timeout).header("X-Internal-Token", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("situation lookup failed with status " + response.statusCode());
        if (response.body().length > 1_000_000) throw new IllegalStateException("situation lookup response exceeds the size limit");
        return mapper.readTree(response.body());
    }

    public JsonNode lookupAbility(UUID authenticatedOwner, JsonNode confirmedScope, String abilityId) throws Exception {
        if (confirmedScope == null || !confirmedScope.isObject()
                || !authenticatedOwner.toString().equals(confirmedScope.path("ownerId").asText()))
            throw new SecurityException("ability lookup scope owner mismatch");
        String sessionId = confirmedScope.path("sessionId").asText("");
        String adventureId = confirmedScope.path("adventureId").asText("");
        String characterSheetId = confirmedScope.path("characterSheetId").asText("");
        if (sessionId.isBlank() || adventureId.isBlank() || characterSheetId.isBlank())
            throw new IllegalArgumentException("ability lookup scope is incomplete");
        if (abilityId == null || abilityId.isBlank() || abilityId.length() > 200)
            throw new IllegalArgumentException("ability ID must contain 1 to 200 characters");
        URI abilityEndpoint = baseUri.resolve("internal/v1/adventures/abilities/lookup");
        var payload = mapper.createObjectNode().put("ownerId", authenticatedOwner.toString())
                .put("sessionId", sessionId).put("adventureId", adventureId)
                .put("characterSheetId", characterSheetId).put("abilityId", abilityId.trim());
        HttpRequest request = HttpRequest.newBuilder(abilityEndpoint).timeout(timeout)
                .header("X-Internal-Token", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("ability lookup failed with status " + response.statusCode());
        if (response.body().length > 1_000_000) throw new IllegalStateException("ability lookup response exceeds the size limit");
        return mapper.readTree(response.body());
    }
}
