package com.dndmaster.aigamemaster.configuration;

import java.net.URI;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Establishes the validated local URL as the sole source for Spring AI clients. */
public final class LocalOllamaEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String LOCAL_BASE_URL = "local-ai.ollama.base-url";
    static final String SPRING_AI_BASE_URL = "spring.ai.ollama.base-url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configured = environment.getProperty(LOCAL_BASE_URL);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Local Ollama base URL is required");
        }
        URI validated = validateLoopback(configured);
        String independentOverride = environment.getProperty(SPRING_AI_BASE_URL);
        if (independentOverride != null && !sameEndpoint(validated, validateLoopback(independentOverride))) {
            throw new IllegalStateException("Independent Spring AI Ollama base URL override is forbidden");
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "validatedLocalOllamaBaseUrl", Map.of(SPRING_AI_BASE_URL, validated.toString())));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private static URI validateLoopback(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Ollama base URL is invalid", exception);
        }
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!"http".equalsIgnoreCase(uri.getScheme()) || !loopback || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Ollama base URL must be an HTTP loopback endpoint");
        }
        return uri.normalize();
    }

    private static boolean sameEndpoint(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right)
                && normalizedPath(left).equals(normalizedPath(right));
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 80 : uri.getPort();
    }

    private static String normalizedPath(URI uri) {
        return uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
    }
}
