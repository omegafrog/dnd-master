package com.dndmaster.aigamemaster.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.*;

class OpenAiResponsesCharacterTagProviderTest {
    private WireMockServer server;

    @BeforeEach void start() { server = new WireMockServer(WireMockConfiguration.options().dynamicPort()); server.start(); }
    @AfterEach void stop() { server.stop(); }

    @Test void sendsCodexMiniResponsesRequestAndReadsOutputText() {
        server.stubFor(post("/v1/responses").willReturn(okJson("""
                {"output":[{"type":"message","content":[{"type":"output_text","text":"[{\\"key\\":\\"race\\"}]"}]}]}
                """)));

        OpenAiResponsesCharacterTagProvider provider = new OpenAiResponsesCharacterTagProvider(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), "test-key", "codex-mini-latest", Duration.ofSeconds(1));

        assertEquals("[{\"key\":\"race\"}]", provider.complete("operation-1", "extract race"));
        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("codex-mini-latest")))
                .withRequestBody(matchingJsonPath("$.input", equalTo("extract race")))
                .withRequestBody(matchingJsonPath("$.text.format.type", equalTo("json_object"))));
    }

    @Test void mapsRateLimitToExistingRetryableFailure() {
        server.stubFor(post("/v1/responses").willReturn(aResponse().withStatus(429)));
        OpenAiResponsesCharacterTagProvider provider = new OpenAiResponsesCharacterTagProvider(
                HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"), "test-key", "codex-mini-latest", Duration.ofSeconds(1));

        assertThrows(ProviderRateLimitException.class, () -> provider.complete("operation-2", "extract race"));
    }
}
