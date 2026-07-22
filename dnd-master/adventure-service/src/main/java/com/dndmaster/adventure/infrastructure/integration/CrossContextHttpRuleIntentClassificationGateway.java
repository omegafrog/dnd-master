package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.combat.CrossContextCallException;
import com.dndmaster.adventure.application.guidance.RuleIntentClassificationPort;
import com.dndmaster.adventure.application.guidance.RuleQueryIntent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class CrossContextHttpRuleIntentClassificationGateway implements RuleIntentClassificationPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpRuleIntentClassificationGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RuleQueryIntent classify(String situation) {
        try {
            String body = objectMapper.writeValueAsString(new IntentClassificationRequest(situation));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/intent-classifications"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("cross-context intent classification failed with status " + response.statusCode());
            }
            IntentClassificationResponse payload = objectMapper.readValue(response.body(), IntentClassificationResponse.class);
            return RuleQueryIntent.fromWireValue(payload.queryIntent());
        } catch (IOException exception) {
            throw new CrossContextCallException("cross-context intent classification failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("cross-context intent classification interrupted", exception);
        }
    }

    record IntentClassificationRequest(String question) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntentClassificationResponse(String queryIntent) {}
}
