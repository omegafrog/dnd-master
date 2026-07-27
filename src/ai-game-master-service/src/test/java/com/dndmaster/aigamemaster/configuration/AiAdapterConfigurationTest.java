package com.dndmaster.aigamemaster.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
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
}
