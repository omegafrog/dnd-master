package com.dndmaster.ruleknowledge.configuration;

import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.dndmaster.ruleknowledge.infrastructure.ai.OllamaStructureDetectionAdapter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StructureDetectionConfiguration {
    @Bean
    StructureDetectionPort structureDetectionPort(
            @Value("${local-ai.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${local-ai.ollama.chat-model:qwen3.5:4b}") String chatModel,
            @Value("${local-ai.ollama.request-timeout:5s}") Duration requestTimeout) {
        return new OllamaStructureDetectionAdapter(baseUrl, chatModel, requestTimeout);
    }
}
