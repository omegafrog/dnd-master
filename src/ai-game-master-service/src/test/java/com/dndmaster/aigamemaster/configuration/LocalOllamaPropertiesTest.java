package com.dndmaster.aigamemaster.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalOllamaPropertiesTest {
    @Test
    void acceptsLoopbackDefaultAndQualityModelsOnly() {
        assertDoesNotThrow(() -> properties(URI.create("http://127.0.0.1:11434"), LocalOllamaProperties.DEFAULT_CHAT_MODEL).validate());
        assertDoesNotThrow(() -> properties(URI.create("http://localhost:11434"), LocalOllamaProperties.QUALITY_CHAT_MODEL).validate());
    }

    @Test
    void rejectsNonLoopbackAndNonAllowlistedModels() {
        assertThrows(IllegalStateException.class, () -> properties(URI.create("http://ollama.example:11434"), LocalOllamaProperties.DEFAULT_CHAT_MODEL).validate());
        assertThrows(IllegalStateException.class, () -> properties(URI.create("http://127.0.0.1:11434"), "unapproved:model").validate());
    }

    private static LocalOllamaProperties properties(URI baseUrl, String chatModel) {
        return new LocalOllamaProperties(baseUrl, chatModel, LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL,
                Set.of(LocalOllamaProperties.DEFAULT_CHAT_MODEL, LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL),
                Duration.ofSeconds(5), 3,
                Duration.ofSeconds(30), 2);
    }
}
