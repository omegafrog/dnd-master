package com.dndmaster.aigamemaster.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class AiAdapterConfigurationTest {
    @Test
    void connectsBootProvidedChatModelToExistingAdapter() {
        LocalOllamaProperties properties = new LocalOllamaProperties(
                URI.create("http://127.0.0.1:11434"),
                LocalOllamaProperties.DEFAULT_CHAT_MODEL,
                LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL,
                Set.of(LocalOllamaProperties.DEFAULT_CHAT_MODEL, LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL),
                Duration.ofSeconds(5),
                3,
                Duration.ofSeconds(30),
                2);

        assertNotNull(new AiAdapterConfiguration().springAiChatAdapter(mock(ChatModel.class), properties));
    }

    @Test
    void selectsOpenAiCodexForCharacterTagExtraction() {
        CharacterTagModelProperties properties = new CharacterTagModelProperties(
                CharacterTagModelProperties.OPENAI_CODEX, URI.create("http://127.0.0.1:8089/"),
                "test-key", "codex-mini-latest", Duration.ofSeconds(5), "codex", java.nio.file.Path.of("/tmp"));

        SpringAiChatAdapter ollama = new SpringAiChatAdapter(mock(ChatModel.class), 1, new SafeAiAuditLogger(message -> { }));
        LocalOllamaProperties local = new LocalOllamaProperties(URI.create("http://127.0.0.1:11434"),
                LocalOllamaProperties.DEFAULT_CHAT_MODEL, LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL,
                Set.of(LocalOllamaProperties.DEFAULT_CHAT_MODEL, LocalOllamaProperties.DEFAULT_EMBEDDING_MODEL),
                Duration.ofSeconds(5), 3, Duration.ofSeconds(30), 2);
        assertNotNull(new AiAdapterConfiguration().characterTagCompletionPort(ollama, properties, local, new ObjectMapper()));
    }
}
