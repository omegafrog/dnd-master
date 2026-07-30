package com.dndmaster.aigamemaster.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("local-ai.ollama")
public record LocalOllamaProperties(
        URI baseUrl,
        String chatModel,
        String embeddingModel,
        Set<String> allowedModels,
        Duration requestTimeout,
        int circuitFailureThreshold,
        Duration circuitResetTimeout,
        int retryMaxAttempts) {

    public static final String DEFAULT_CHAT_MODEL = "exaone3.5:7.8b";
    public static final String DEFAULT_EMBEDDING_MODEL = "qwen3-embedding:0.6b";
    public static final String QUALITY_CHAT_MODEL = "exaone3.5:7.8b";

    public LocalOllamaProperties {
        allowedModels = Set.copyOf(allowedModels == null ? Set.of() : allowedModels);
    }

    public void validate() {
        if (baseUrl == null || baseUrl.getHost() == null || !isLoopback(baseUrl.getHost())) {
            throw new IllegalStateException("Ollama base URL must use a loopback host");
        }
        if (!allowedModels.contains(chatModel) || !allowedModels.contains(embeddingModel)) {
            throw new IllegalStateException("Configured Ollama model is not allowlisted");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                || circuitFailureThreshold < 1 || circuitResetTimeout == null
                || circuitResetTimeout.isNegative() || circuitResetTimeout.isZero() || retryMaxAttempts < 1) {
            throw new IllegalStateException("Ollama timeout, circuit and retry policy must be positive");
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }
}
