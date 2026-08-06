package com.dndmaster.aigamemaster.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.gm")
public record GmProviderProperties(String provider, String model, String reasoning, URI baseUrl, String apiKey, Duration timeout) {
    public GmProviderProperties {
        provider = provider == null || provider.isBlank() ? "ollama" : provider.trim().toLowerCase();
        model = model == null || model.isBlank() ? "qwen3:8b" : model.trim();
        reasoning = reasoning == null || reasoning.isBlank() ? "medium" : reasoning.trim().toLowerCase();
        baseUrl = baseUrl == null ? URI.create("https://api.openai.com/") : baseUrl;
        apiKey = apiKey == null ? "" : apiKey.trim();
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
    }
    public void validate() {
        if (!provider.equals("ollama") && !provider.equals("openai")) throw new IllegalStateException("unsupported GM provider: " + provider);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalStateException("GM provider timeout must be positive");
        if (provider.equals("openai") && apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY required for ai.gm.provider=openai");
    }
}
