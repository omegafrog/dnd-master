package com.dndmaster.aigamemaster.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.configuration.LocalOllamaProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaStartupPreflightTest {
    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void acceptsLoopbackRuntimeWithBothRequiredModelsWithoutPulling() {
        server.stubFor(get("/api/tags").willReturn(okJson("{\"models\":[{\"name\":\"qwen3:8b\"},{\"name\":\"qwen3-embedding:0.6b\"}]}")));

        assertDoesNotThrow(() -> new OllamaStartupPreflight(properties(), new OllamaStartupPreflight.HttpOllamaModelInventory()).verify());
        server.verify(getRequestedFor(urlEqualTo("/api/tags")));
    }

    @Test
    void rejectsMissingRequiredModel() {
        OllamaStartupPreflight.OllamaModelInventory missingEmbedding = (url, timeout) -> Set.of(LocalOllamaProperties.DEFAULT_CHAT_MODEL);

        assertThrows(IllegalStateException.class, () -> new OllamaStartupPreflight(properties(), missingEmbedding).verify());
    }

    @Test
    void rejectsUnavailableRuntime() {
        OllamaStartupPreflight.OllamaModelInventory unavailable = (url, timeout) -> {
            throw new IllegalStateException("Ollama runtime is unavailable");
        };

        assertThrows(IllegalStateException.class, () -> new OllamaStartupPreflight(properties(), unavailable).verify());
    }

    private LocalOllamaProperties properties() {
        return new LocalOllamaProperties(URI.create(server.baseUrl()), LocalOllamaProperties.DEFAULT_CHAT_MODEL,
                LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL,
                Set.of(LocalOllamaProperties.DEFAULT_CHAT_MODEL, LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL),
                Duration.ofSeconds(5), 3, Duration.ofSeconds(30), 2);
    }
}
