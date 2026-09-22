package com.dndmaster.character.infrastructure;

import com.dndmaster.character.application.auth.PlayerSessionLookupPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public final class CrossContextHttpPlayerSessionLookupGateway implements PlayerSessionLookupPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public CrossContextHttpPlayerSessionLookupGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper objectMapper, String internalToken) {
        this.client = client;
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        if (internalToken == null || internalToken.isBlank()) throw new IllegalArgumentException("internal token must not be blank");
        this.internalToken = internalToken;
    }

    @Override
    public Optional<UUID> resolvePlayerId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return Optional.empty();
        try {
            String body = objectMapper.writeValueAsString(new IntrospectionRequest(accessToken));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/auth/introspections"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return Optional.empty();
            IntrospectionResponse result = objectMapper.readValue(response.body(), IntrospectionResponse.class);
            if (!result.authenticated() || result.playerId() == null || result.playerId().isBlank()) return Optional.empty();
            return Optional.of(UUID.fromString(result.playerId()));
        } catch (IOException exception) {
            throw new IllegalStateException("player session lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("player session lookup interrupted", exception);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    record IntrospectionRequest(String token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntrospectionResponse(boolean authenticated, String playerId) {}
}
