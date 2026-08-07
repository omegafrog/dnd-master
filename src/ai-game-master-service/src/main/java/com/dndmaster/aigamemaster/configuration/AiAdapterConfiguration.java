package com.dndmaster.aigamemaster.configuration;

import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CharacterTagCompletionPort;
import com.dndmaster.aigamemaster.infrastructure.ai.OpenAiResponsesCharacterTagProvider;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexCliCharacterTagProvider;
import com.dndmaster.aigamemaster.infrastructure.ai.OllamaThinkingCharacterTagProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiAdapterConfiguration {
    @Bean
    SpringAiChatAdapter springAiChatAdapter(ChatModel chatModel, LocalOllamaProperties properties) {
        properties.validate();
        SafeAiAuditLogger auditLogger = new SafeAiAuditLogger(
                message -> LoggerFactory.getLogger(SpringAiChatAdapter.class).info(message));
        return new SpringAiChatAdapter(chatModel, properties.retryMaxAttempts(), auditLogger,
                properties.chatModel().toLowerCase(java.util.Locale.ROOT).contains("thinking"), properties.numPredict());
    }

    @Bean
    CharacterTagCompletionPort characterTagCompletionPort(
            SpringAiChatAdapter ollamaAdapter, CharacterTagModelProperties properties,
            LocalOllamaProperties ollamaProperties, ObjectMapper objectMapper) {
        if (CharacterTagModelProperties.OLLAMA.equals(properties.provider())) {
            if (ollamaProperties.chatModel().toLowerCase(java.util.Locale.ROOT).contains("thinking")) {
                OllamaThinkingCharacterTagProvider provider = new OllamaThinkingCharacterTagProvider(HttpClient.newHttpClient(),
                        ollamaProperties.baseUrl(), ollamaProperties.chatModel(), ollamaProperties.requestTimeout(), objectMapper);
                return provider::complete;
            }
            return (operationId, prompt) -> ollamaAdapter.complete(operationId, prompt, value -> value);
        }
        if (CharacterTagModelProperties.CODEX_CLI.equals(properties.provider())) {
            properties.validateCodexCli();
            CodexCliCharacterTagProvider provider = new CodexCliCharacterTagProvider(
                    properties.executable(), properties.model(), properties.workDirectory(), properties.timeout());
            return provider::complete;
        }
        properties.validateOpenAi();
        OpenAiResponsesCharacterTagProvider provider = new OpenAiResponsesCharacterTagProvider(
                HttpClient.newHttpClient(), properties.baseUrl(), properties.apiKey(), properties.model(), properties.timeout());
        return provider::complete;
    }
}
