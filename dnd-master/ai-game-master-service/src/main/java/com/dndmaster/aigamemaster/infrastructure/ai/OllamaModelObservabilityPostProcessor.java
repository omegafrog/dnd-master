package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.LocalOllamaProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public final class OllamaModelObservabilityPostProcessor implements BeanPostProcessor, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaModelObservabilityPostProcessor.class);

    private final LocalOllamaProperties properties;
    private final ExecutorService deadlineExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final OllamaCallObservability chatObservability;
    private final OllamaCallObservability embeddingObservability;

    public OllamaModelObservabilityPostProcessor(LocalOllamaProperties properties) {
        properties.validate();
        this.properties = properties;
        this.chatObservability = observability("chat");
        this.embeddingObservability = observability("embedding");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ChatModel chatModel && !(bean instanceof ObservedOllamaChatModel)) {
            return new ObservedOllamaChatModel(
                    chatModel, properties.requestTimeout(), deadlineExecutor, chatObservability);
        }
        if (bean instanceof EmbeddingModel embeddingModel && !(bean instanceof ObservedOllamaEmbeddingModel)) {
            return new ObservedOllamaEmbeddingModel(embeddingModel, embeddingObservability);
        }
        return bean;
    }

    public OllamaCallObservability chatObservability() {
        return chatObservability;
    }

    public OllamaCallObservability embeddingObservability() {
        return embeddingObservability;
    }

    @Override
    public void close() {
        deadlineExecutor.close();
    }

    private OllamaCallObservability observability(String kind) {
        return new OllamaCallObservability(kind, properties.circuitFailureThreshold(),
                properties.circuitResetTimeout(), LOGGER::info);
    }
}
