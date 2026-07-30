package com.dndmaster.aigamemaster.configuration;

import java.net.URI;
import java.time.Duration;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.character-tags")
public record CharacterTagModelProperties(String provider, URI baseUrl, String apiKey, String model, Duration timeout,
                                          String executable, Path workDirectory) {
    public static final String OPENAI_CODEX = "openai-codex";
    public static final String CODEX_CLI = "codex-cli";
    public static final String OLLAMA = "ollama";

    public CharacterTagModelProperties {
        provider = provider == null || provider.isBlank() ? OLLAMA : provider.trim().toLowerCase();
        baseUrl = baseUrl == null ? URI.create("https://api.openai.com/") : baseUrl;
        model = model == null || model.isBlank() ? "codex-mini-latest" : model.trim();
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        apiKey = apiKey == null ? "" : apiKey.trim();
        executable = executable == null || executable.isBlank() ? "codex" : executable.trim();
        workDirectory = workDirectory == null ? Path.of(System.getProperty("java.io.tmpdir")) : workDirectory;
    }

    public void validateOpenAi() {
        if (!OPENAI_CODEX.equals(provider)) throw new IllegalStateException("unsupported character tag provider: " + provider);
        if (apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is required for ai.character-tags.provider=openai-codex");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalStateException("character tag provider timeout must be positive");
    }

    public void validateCodexCli() {
        if (!CODEX_CLI.equals(provider)) throw new IllegalStateException("unsupported character tag provider: " + provider);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalStateException("character tag provider timeout must be positive");
    }
}
