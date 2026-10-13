package com.dndmaster.aigamemaster.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral GM completion over OpenAI Responses API. */
public final class OpenAiGmProvider implements GmCompletionAdapter {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OpenAiGmProvider.class);
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
        return complete(operationId, new GmPrompt(prompt), parser);
    }

    @Override public <T> T complete(String operationId, GmPrompt prompt, StructuredResponseParser<T> parser) {
        return complete(operationId, prompt, parser, null);
    }

    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser, JsonNode outputSchema) {
        return complete(operationId, new GmPrompt(prompt), parser, outputSchema);
    }

    private <T> T complete(String operationId, GmPrompt prompt, StructuredResponseParser<T> parser, JsonNode outputSchema) {
        try {
            Object input = prompt.hasImage()
                    ? List.of(Map.of("role", "user", "content", List.of(
                            Map.of("type", "input_text", "text", prompt.text()),
                            Map.of("type", "input_image", "image_url", prompt.imageDataUri()))))
                    : prompt.text();
            Object format = outputSchema == null ? Map.of("type", "json_object")
                    : Map.of("type", "json_schema", "name", "gm_response", "strict", false,
                            "schema", mapper.convertValue(outputSchema, Object.class));
            String body = mapper.writeValueAsString(Map.of("model", model, "input", input,
                    "store", false, "reasoning", Map.of("effort", reasoning),
                    "text", Map.of("format", format),
                    "metadata", Map.of("operation_id", required(operationId, "operation id"))));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("v1/responses"))
                    .timeout(timeout).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderRateLimitException();
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("OpenAI Responses request failed");
            JsonNode payload = mapper.readTree(response.body());
            logUsage(operationId, payload.path("usage"));
            return parser.parse(outputText(payload));
        } catch (java.net.http.HttpTimeoutException exception) { throw new ProviderTimeoutException(exception);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderTimeoutException(exception);
        } catch (IOException exception) { throw new IllegalStateException("OpenAI Responses request failed", exception); }
    }

    private String outputText(JsonNode payload) {
        JsonNode output = payload.path("output");
        if (!output.isArray()) throw new ProviderMalformedResponseException("OpenAI response missing output");
        for (JsonNode item : output) for (JsonNode content : item.path("content")) {
            if ("output_text".equals(content.path("type").asText()) && !content.path("text").asText().isBlank()) return content.path("text").asText();
        }
        throw new ProviderMalformedResponseException("OpenAI response missing output text");
    }

    private static void logUsage(String operationId, JsonNode usage) {
        if (!usage.isObject()) return;
        com.dndmaster.aigamemaster.application.ai.AiExecutionUsage counts = reportedUsage(usage);
        LOGGER.info("openai_response_usage operationId={} inputTokens={} cachedInputTokens={} outputTokens={}",
                operationId, counts.inputTokens(), counts.cachedInputTokens(), counts.outputTokens());
    }

    static com.dndmaster.aigamemaster.application.ai.AiExecutionUsage reportedUsage(JsonNode usage) {
        return new com.dndmaster.aigamemaster.application.ai.AiExecutionUsage(count(usage.path("input_tokens")),
                count(usage.path("input_tokens_details").path("cached_tokens")), count(usage.path("output_tokens")));
    }

    private static Long count(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0 ? node.longValue() : null;
    }

    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); return value.trim(); }
}
