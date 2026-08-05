package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;

public final class HttpCharacterToolPort implements OfficialToolPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper; private final String token;
    public HttpCharacterToolPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) {
        this.client = Objects.requireNonNull(client); this.baseUri = baseUri; this.timeout = timeout; this.mapper = mapper; this.token = token;
    }
    public GmToolOutcome execute(GmToolInvocation invocation) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(invocation.argumentsJson());
            String sheetId = body.path("characterSheetId").asText();
            if (sheetId.isBlank()) throw new IllegalArgumentException("characterSheetId required");
            URI endpoint = baseUri.resolve("internal/v1/character-sheets/" + java.util.UUID.fromString(sheetId));
            long expectedVersion = body.path("expectedVersion").asLong(-1);
            if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion required");
            body.remove("characterSheetId");
            body.remove("expectedVersion");
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", token).header("Idempotency-Key", invocation.invocationId().toString()).header("If-Match-Version", Long.toString(expectedVersion)).PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return GmToolOutcome.rejected("character service rejected command: " + response.statusCode());
            return GmToolOutcome.completed(response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return GmToolOutcome.rejected("character service interrupted"); }
        catch (Exception e) { return GmToolOutcome.rejected("character command arguments invalid"); }
    }
}
