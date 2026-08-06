package com.dndmaster.aigamemaster.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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
                "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"scene\\\":\\\"crypt\\\"}\"}]}]}")));
        GmCompletionAdapter adapter = new OpenAiGmProvider(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                "test-key", "gpt-5.6-luna", "medium", Duration.ofSeconds(1));

        assertEquals("crypt", adapter.complete("turn-1", "grounded context", value -> value.substring(10, value.length() - 2)));
        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-5.6-luna")))
                .withRequestBody(matchingJsonPath("$.reasoning.effort", equalTo("medium")))
                .withRequestBody(matchingJsonPath("$.text.format.type", equalTo("json_object"))));
    }
}
