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

/** Provider-neutral GM completion over OpenAI Responses API. */
public final class OpenAiGmProvider implements GmCompletionAdapter {
    private final HttpClient client;
    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final String reasoning;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiGmProvider(HttpClient client, URI baseUri, String apiKey, String model, String reasoning, Duration timeout) {
        this.client = Objects.requireNonNull(client); this.baseUri = Objects.requireNonNull(baseUri);
        this.apiKey = required(apiKey, "OpenAI API key"); this.model = required(model, "OpenAI model");
        this.reasoning = required(reasoning, "reasoning"); this.timeout = Objects.requireNonNull(timeout);
    }

    @Override public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
        try {
            String body = mapper.writeValueAsString(Map.of("model", model, "input", required(prompt, "prompt"),
                    "store", false, "reasoning", Map.of("effort", reasoning),
                    "text", Map.of("format", Map.of("type", "json_object")),
                    "metadata", Map.of("operation_id", required(operationId, "operation id"))));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("v1/responses"))
                    .timeout(timeout).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderRateLimitException();
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("OpenAI Responses request failed");
            return parser.parse(outputText(response.body()));
        } catch (java.net.http.HttpTimeoutException exception) { throw new ProviderTimeoutException(exception);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderTimeoutException(exception);
        } catch (IOException exception) { throw new IllegalStateException("OpenAI Responses request failed", exception); }
    }

    private String outputText(String payload) throws IOException {
        JsonNode output = mapper.readTree(payload).path("output");
        if (!output.isArray()) throw new ProviderMalformedResponseException("OpenAI response missing output");
        for (JsonNode item : output) for (JsonNode content : item.path("content")) {
            if ("output_text".equals(content.path("type").asText()) && !content.path("text").asText().isBlank()) return content.path("text").asText();
        }
        throw new ProviderMalformedResponseException("OpenAI response missing output text");
    }

    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); return value.trim(); }
}
