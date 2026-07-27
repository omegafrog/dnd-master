package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CrossContextHttpPlayerSessionLookupGateway implements PlayerSessionLookupPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpPlayerSessionLookupGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<UUID> resolvePlayerId(String accessToken) {
        try {
            String body = objectMapper.writeValueAsString(new IntrospectionRequest(accessToken));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/auth/introspections"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            IntrospectionResponse introspection = objectMapper.readValue(response.body(), IntrospectionResponse.class);
            if (!introspection.authenticated() || introspection.playerId() == null || introspection.playerId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(introspection.playerId()));
        } catch (IOException exception) {
            throw new PlayerSessionLookupException("player session lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlayerSessionLookupException("player session lookup interrupted", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntrospectionRequest(String token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntrospectionResponse(boolean authenticated, String playerId) {}

    public static final class PlayerSessionLookupException extends RuntimeException {
        public PlayerSessionLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
