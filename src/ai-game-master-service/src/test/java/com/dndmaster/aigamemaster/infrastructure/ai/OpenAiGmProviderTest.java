package com.dndmaster.aigamemaster.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.*;

class OpenAiGmProviderTest {
    private WireMockServer server;

    @BeforeEach void start() { server = new WireMockServer(WireMockConfiguration.options().dynamicPort()); server.start(); }
    @AfterEach void stop() { server.stop(); }

    @Test void sendsStructuredResponsesRequestAndUsesCanonicalParser() {
        server.stubFor(post("/v1/responses").willReturn(okJson(
                "{\"usage\":{\"input_tokens\":2048,\"input_tokens_details\":{\"cached_tokens\":1536},\"output_tokens\":128},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"scene\\\":\\\"crypt\\\"}\"}]}]}")));
        GmCompletionAdapter adapter = new OpenAiGmProvider(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                "test-key", "gpt-5.6-luna", "medium", Duration.ofSeconds(1));

        assertEquals("crypt", adapter.complete("turn-1", "grounded context", value -> value.substring(10, value.length() - 2)));
        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-5.6-luna")))
                .withRequestBody(matchingJsonPath("$.reasoning.effort", equalTo("medium")))
                .withRequestBody(matchingJsonPath("$.text.format.type", equalTo("json_object"))));
    }

    @Test void sends_supplied_json_schema_with_dynamic_model_and_array_diagnostics() throws Exception {
        server.stubFor(post("/v1/responses").willReturn(okJson(
                "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{}\"}]}]}")));
        var schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"type":"object","additionalProperties":false,"required":["status","scenarioModel","diagnostics"],
                 "properties":{"status":{"type":"string","enum":["READY","BLOCKED"]},
                 "scenarioModel":{"type":["object","null"],"additionalProperties":true},
                 "diagnostics":{"type":"array","items":{"type":"string"}}}}
                """);
        var provider = (OpenAiGmProvider) provider(Duration.ofSeconds(1));

        provider.complete("scenario-compilation", "grounded context", value -> value, schema);

        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withRequestBody(matchingJsonPath("$.text.format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.text.format.strict", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.text.format.schema.properties.diagnostics.type", equalTo("array")))
                .withRequestBody(matchingJsonPath("$.text.format.schema.properties.diagnostics.items.type", equalTo("string")))
                .withRequestBody(matchingJsonPath("$.text.format.schema.properties.scenarioModel.additionalProperties", equalTo("true"))));
    }

    @Test void reads_cached_input_usage_reported_by_responses_api() throws Exception {
        var usage = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"input_tokens\":2048,\"input_tokens_details\":{\"cached_tokens\":1536},\"output_tokens\":128}");

        assertEquals(new com.dndmaster.aigamemaster.application.ai.AiExecutionUsage(2048L, 1536L, 128L),
                OpenAiGmProvider.reportedUsage(usage));
    }

    @Test void maps_rate_limit_to_provider_contract_error() {
        server.stubFor(post("/v1/responses").willReturn(aResponse().withStatus(429)));
        GmCompletionAdapter adapter = provider(Duration.ofSeconds(1));

        assertThrows(ProviderRateLimitException.class,
                () -> adapter.complete("turn-rate-limit", "grounded context", value -> value));
    }

    @Test void sends_map_image_as_responses_input_image() {
        server.stubFor(post("/v1/responses").willReturn(okJson(
                "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{}\"}]}]}")));
        GmCompletionAdapter adapter = provider(Duration.ofSeconds(1));

        adapter.complete("map-image", new GmPrompt("inspect map", "data:image/png;base64,AQ=="), value -> value);

        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withRequestBody(matchingJsonPath("$.input[0].content[0].type", equalTo("input_text")))
                .withRequestBody(matchingJsonPath("$.input[0].content[1].type", equalTo("input_image")))
                .withRequestBody(matchingJsonPath("$.input[0].content[1].image_url", equalTo("data:image/png;base64,AQ=="))));
    }

    @Test void rejects_missing_structured_output_with_provider_contract_error() {
        server.stubFor(post("/v1/responses").willReturn(okJson("{\"output\":[]}")));
        GmCompletionAdapter adapter = provider(Duration.ofSeconds(1));

        assertThrows(ProviderMalformedResponseException.class,
                () -> adapter.complete("turn-malformed", "grounded context", value -> value));
    }

    private GmCompletionAdapter provider(Duration timeout) {
        return new OpenAiGmProvider(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                "test-key", "gpt-5.6-luna", "medium", timeout);
    }
}
