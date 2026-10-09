package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.preparation.InitialSituationGmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Calls the authenticated GM boundary for private initial-situation preparation. */
public final class HttpInitialSituationGmPort implements InitialSituationGmPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpInitialSituationGmPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override public JsonNode generate(Request request) {
        try {
            String body = mapper.writeValueAsString(new GmRequest(request.ownerPlayerId(), request.endpointId(),
                    request.provider(), request.model(), request.reasoning(), request.operationKey(), request.prompt(),
                    request.outputSchema()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/gm/initial-situation"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw HttpScenarioCompilationAgentPort.decodeFailure(response.statusCode(), response.body(), request.operationKey());
            }
            JsonNode result = mapper.readTree(response.body());
            if (result == null || !result.isObject()) throw new IllegalStateException("initial situation GM returned invalid JSON object");
            return result;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("initial situation GM request interrupted", failure);
        } catch (Exception failure) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("initial situation GM request failed", failure);
        }
    }

    private record GmRequest(java.util.UUID soloPlayerId, java.util.UUID endpointId, String provider, String model,
            String reasoning, String operationKey, String prompt, JsonNode outputSchema) {}
}
