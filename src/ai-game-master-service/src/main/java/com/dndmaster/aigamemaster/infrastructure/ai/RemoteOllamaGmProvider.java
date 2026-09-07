package com.dndmaster.aigamemaster.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.List;

/** Ollama HTTP adapter used for an endpoint selected at runtime by Backoffice. */
public final class RemoteOllamaGmProvider implements GmCompletionAdapter {
    private final HttpClient client; private final URI baseUrl; private final String model; private final Duration timeout; private final ObjectMapper mapper = new ObjectMapper();
    public RemoteOllamaGmProvider(HttpClient client, URI baseUrl, String model, Duration timeout) { this.client = client; this.baseUrl = baseUrl; this.model = model; this.timeout = timeout; }
    @Override public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
        return complete(operationId, new GmPrompt(prompt), parser);
    }
    @Override public <T> T complete(String operationId, GmPrompt prompt, StructuredResponseParser<T> parser) {
        try {
            Map<String, Object> request = new java.util.HashMap<>(Map.of("model", model, "prompt", prompt.text(), "stream", false, "format", "json"));
            if (prompt.hasImage()) {
                String encoded = prompt.imageDataUri().substring(prompt.imageDataUri().indexOf(',') + 1);
                request.put("images", List.of(encoded));
            }
            String body = mapper.writeValueAsString(request);
            var response = client.send(HttpRequest.newBuilder(baseUrl.resolve("/api/generate")).timeout(timeout).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Ollama request failed");
            String text = mapper.readTree(response.body()).path("response").asText();
            if (text.isBlank()) throw new ProviderMalformedResponseException("Ollama response missing text");
            return parser.parse(text);
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ProviderTimeoutException(error);
        } catch (java.net.http.HttpTimeoutException error) { throw new ProviderTimeoutException(error);
        } catch (Exception error) { throw new IllegalStateException("Ollama request failed", error); }
    }
}
