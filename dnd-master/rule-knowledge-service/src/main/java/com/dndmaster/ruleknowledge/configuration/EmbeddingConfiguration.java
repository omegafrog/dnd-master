package com.dndmaster.ruleknowledge.configuration;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.infrastructure.ai.OllamaEmbeddingAdapter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {
    @Bean
    EmbeddingPort embeddingPort(EmbeddingModel embeddingModel) {
        return new OllamaEmbeddingAdapter(embeddingModel);
    }
}
