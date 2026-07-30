package com.dndmaster.aigamemaster.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Reads Ollama's separate thinking payload, which Spring AI 1.1.x does not expose. */
public final class OllamaThinkingCharacterTagProvider {
    private final HttpClient client; private final URI endpoint; private final String model; private final Duration timeout; private final ObjectMapper mapper;

    public OllamaThinkingCharacterTagProvider(HttpClient client, URI baseUrl, String model, Duration timeout, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client); this.endpoint = Objects.requireNonNull(baseUrl).resolve("/api/generate");
        this.model = Objects.requireNonNull(model); this.timeout = Objects.requireNonNull(timeout); this.mapper = Objects.requireNonNull(mapper);
    }

    public String complete(String operationId, String prompt) {
        try {
            String body = mapper.writeValueAsString(new Request(model, prompt, false, true, "json", new Options(4096)));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("Ollama thinking request failed with status " + response.statusCode());
            Response parsed = mapper.readValue(response.body(), Response.class);
            String value = parsed.response() == null || parsed.response().isBlank() ? parsed.thinking() : parsed.response();
            if (value == null || value.isBlank()) throw new ProviderMalformedResponseException("Ollama thinking response missing text");
            return value;
        } catch (IOException exception) { throw new IllegalStateException("Ollama thinking request failed", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderTimeoutException(exception); }
    }

    record Request(String model, String prompt, boolean stream, boolean think, String format, Options options) {}
    record Options(int num_predict) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(String response, String thinking) {}
}
