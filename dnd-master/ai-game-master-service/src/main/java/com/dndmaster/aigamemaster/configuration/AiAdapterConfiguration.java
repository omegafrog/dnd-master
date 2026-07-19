package com.dndmaster.aigamemaster.configuration;

import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
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
        return new SpringAiChatAdapter(chatModel, properties.circuitFailureThreshold(), auditLogger);
    }
}
