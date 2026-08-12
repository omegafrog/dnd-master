package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.net.http.HttpClient;
import java.util.Objects;
import java.nio.file.Path;
import java.time.Duration;

/** Routes each turn to the provider locked on its session binding. */
public final class GmCompletionRouter implements GmCompletionAdapter {
    private final SpringAiChatAdapter ollama;
    private final GmProviderProperties defaults;
    private final AgentEndpointRegistry endpointRegistry;
    private final String codexExecutable;
    private final Path codexWorkDirectory;
    private final Duration codexTimeout;

    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults) {
        this(ollama, defaults, null);
    }
    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults, AgentEndpointRegistry endpointRegistry) {
        this(ollama, defaults, endpointRegistry, "codex", Path.of("."), defaults.timeout());
    }
    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults, AgentEndpointRegistry endpointRegistry,
                              String codexExecutable, Path codexWorkDirectory, Duration codexTimeout) {
        this.ollama = Objects.requireNonNull(ollama);
        this.defaults = Objects.requireNonNull(defaults);
        this.endpointRegistry = endpointRegistry;
        this.codexExecutable = codexExecutable;
        this.codexWorkDirectory = codexWorkDirectory;
        this.codexTimeout = codexTimeout;
    }

    @Override
    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
        return complete(operationId, prompt, parser,
                new GmProviderRequest(defaults.provider(), defaults.model(), defaults.reasoning()));
    }

    @Override
    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser,
                          GmProviderRequest provider) {
        AgentEndpoint endpoint = endpointRegistry == null ? null : endpointRegistry.active();
        String effectiveProvider = endpoint == null ? provider.provider() : switch (endpoint.provider()) {
            case OLLAMA -> "ollama";
            case OPENAI_COMPATIBLE -> "openai";
            case CODEX_CLI -> "codex-cli";
        };
        String model = endpoint == null ? provider.model() : endpoint.model();
        String routedOperation = operationId + ":" + effectiveProvider + ":" + model;
        return switch (effectiveProvider) {
            case "ollama" -> endpoint == null ? ollama.completeWithModel(routedOperation, prompt, parser, model)
                    : new RemoteOllamaGmProvider(HttpClient.newHttpClient(), endpoint.baseUrl(), model, defaults.timeout()).complete(routedOperation, prompt, parser);
            case "openai" -> new OpenAiGmProvider(HttpClient.newHttpClient(), endpoint == null ? defaults.baseUrl() : endpoint.baseUrl(), endpoint == null ? defaults.apiKey() : System.getenv(endpoint.secretEnvironmentVariable()),
                    model, provider.reasoning(), defaults.timeout()).complete(routedOperation, prompt, parser);
            case "codex-cli" -> parser.parse(new CodexCliStoryPlanAdapter(codexExecutable, model, codexWorkDirectory, codexTimeout)
                    .complete(routedOperation, prompt));
            default -> throw new IllegalArgumentException("unsupported GM provider: " + provider.provider());
        };
    }
}
