package com.dndmaster.aigamemaster.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Small, focused Responses API client for character-tag extraction. */
public final class OpenAiResponsesCharacterTagProvider {
    private final HttpClient client;
    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiResponsesCharacterTagProvider(HttpClient client, URI baseUri, String apiKey, String model, Duration timeout) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.apiKey = required(apiKey, "OpenAI API key");
        this.model = required(model, "OpenAI model");
        this.timeout = Objects.requireNonNull(timeout);
    }

    public String complete(String operationId, String prompt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", required(prompt, "prompt"),
                    "store", false,
                    "text", Map.of("format", Map.of("type", "json_object")),
                    "metadata", Map.of("operation_id", required(operationId, "operation id"))));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("v1/responses"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderRateLimitException();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI Responses request failed with status " + response.statusCode());
            }
            return outputText(response.body());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ProviderTimeoutException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI Responses request failed", exception);
        }
    }

    private String outputText(String payload) throws IOException {
        JsonNode output = objectMapper.readTree(payload).path("output");
        if (!output.isArray()) throw new ProviderMalformedResponseException("OpenAI response missing output");
        for (JsonNode item : output) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText()) && !content.path("text").asText().isBlank()) {
                    return content.path("text").asText();
                }
            }
        }
        throw new ProviderMalformedResponseException("OpenAI response missing output text");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value;
    }
}
