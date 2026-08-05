package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GmToolInvocation;
import com.dndmaster.adventure.application.runtime.GmToolOutcome;
import com.dndmaster.adventure.application.runtime.OfficialToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class HttpDiceToolPort implements OfficialToolPort {
    private final HttpClient client; private final URI endpoint; private final Duration timeout; private final ObjectMapper mapper; private final String token;
    public HttpDiceToolPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) {
        this.client = Objects.requireNonNull(client); this.endpoint = baseUri.resolve("internal/v1/dice-rolls/ai"); this.timeout = timeout; this.mapper = mapper; this.token = token;
    }
    public GmToolOutcome execute(GmToolInvocation invocation) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", token).header("Idempotency-Key", invocation.invocationId().toString()).POST(HttpRequest.BodyPublishers.ofString(invocation.argumentsJson())).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return GmToolOutcome.rejected("dice service rejected command: " + response.statusCode());
            return GmToolOutcome.completed(response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return GmToolOutcome.rejected("dice service interrupted"); }
        catch (Exception e) { return GmToolOutcome.unknown("dice service outcome unknown"); }
    }
    public Optional<GmToolOutcome> query(UUID commandId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("../commands/" + commandId)).timeout(timeout).header("X-Internal-Token", token).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2 ? Optional.of(GmToolOutcome.completed(response.body())) : Optional.empty();
        } catch (Exception e) { return Optional.empty(); }
    }
}
