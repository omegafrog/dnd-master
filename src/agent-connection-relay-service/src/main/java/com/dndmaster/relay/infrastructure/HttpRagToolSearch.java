package com.dndmaster.relay.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Executes the read-only tool query against the existing scoped evidence candidate endpoint. */
public final class HttpRagToolSearch {
    private final HttpClient client; private final URI endpoint; private final String token; private final ObjectMapper mapper;
    private final Duration timeout;
    public HttpRagToolSearch(HttpClient client, URI gateway, Duration timeout, String token, ObjectMapper mapper) {
        this.client = client; this.endpoint = gateway.resolve("internal/v1/evidence-candidates/search");
        this.token = token == null ? "" : token; this.mapper = mapper; this.timeout = timeout;
    }
    public JsonNode search(UUID authenticatedOwner, JsonNode confirmedScope, String query) throws Exception {
        if (confirmedScope == null || !confirmedScope.isObject()) throw new IllegalArgumentException("RAG search is unavailable for this execution");
        if (!authenticatedOwner.toString().equals(confirmedScope.path("ownerId").asText())) throw new SecurityException("search scope owner mismatch");
        if (query == null || query.isBlank() || query.length() > 1000) throw new IllegalArgumentException("search query must contain 1 to 1000 characters");
        com.fasterxml.jackson.databind.node.ObjectNode payload = ((com.fasterxml.jackson.databind.node.ObjectNode) confirmedScope).deepCopy();
        payload.put("query", query.trim()); payload.put("denseLimit", 30); payload.put("bm25Limit", 30);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("X-Internal-Token", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("RAG search failed with status " + response.statusCode());
        if (response.body().length > 1_000_000) throw new IllegalStateException("RAG search response exceeds the size limit");
        JsonNode result = mapper.readTree(response.body());
        if (!authenticatedOwner.toString().equals(result.path("ownerId").asText())) throw new SecurityException("RAG search response owner mismatch");
        return result;
    }
}
