package com.dndmaster.relay.infrastructure;

import com.dndmaster.relay.application.IdentityServicePort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** IdentityServicePort implementation backed by the identity-access service. */
public final class HttpIdentityServiceAdapter implements IdentityServicePort {
    private static final String INTROSPECTION_PATH = "internal/v1/auth/introspections";

    private final HttpClient client;
    private final URI introspectionUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public HttpIdentityServiceAdapter(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this(client, baseUri, timeout, objectMapper, "");
    }

    public HttpIdentityServiceAdapter(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper, String internalToken) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.introspectionUri = withTrailingSlash(Objects.requireNonNull(baseUri, "baseUri must not be null"))
                .resolve(INTROSPECTION_PATH);
        this.timeout = positive(timeout, "timeout");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    @Override
    public UUID introspectUser(String token) {
        if (token == null || token.isBlank()) {
            throw new IdentityServiceException("access token is required");
        }
        try {
            String body = objectMapper.writeValueAsString(new IntrospectionRequest(token));
            HttpRequest request = HttpRequest.newBuilder(introspectionUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IdentityServiceException(
                        "identity-access introspection failed with status " + response.statusCode());
            }

            IntrospectionResponse introspection =
                    objectMapper.readValue(response.body(), IntrospectionResponse.class);
            if (!introspection.authenticated() || introspection.playerId() == null
                    || introspection.playerId().isBlank()) {
                throw new IdentityServiceException("access token is not authenticated");
            }
            try {
                return UUID.fromString(introspection.playerId());
            } catch (IllegalArgumentException exception) {
                throw new IdentityServiceException("identity-access returned an invalid player id", exception);
            }
        } catch (IOException exception) {
            throw new IdentityServiceException("identity-access introspection request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IdentityServiceException("identity-access introspection request interrupted", exception);
        }
    }

    private static URI withTrailingSlash(URI uri) {
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IntrospectionRequest(String token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IntrospectionResponse(boolean authenticated, String playerId) {}

    public static final class IdentityServiceException extends RuntimeException {
        public IdentityServiceException(String message) {
            super(message);
        }

        public IdentityServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
