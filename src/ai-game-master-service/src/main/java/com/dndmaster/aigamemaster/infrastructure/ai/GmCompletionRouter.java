package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.net.http.HttpClient;
import java.util.Objects;

/** Routes each turn to the provider locked on its session binding. */
public final class GmCompletionRouter implements GmCompletionAdapter {
    private final SpringAiChatAdapter ollama;
    private final GmProviderProperties defaults;
    private final AgentEndpointRegistry endpointRegistry;

    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults) {
        this(ollama, defaults, null);
    }
    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults, AgentEndpointRegistry endpointRegistry) {
        this.ollama = Objects.requireNonNull(ollama);
        this.defaults = Objects.requireNonNull(defaults);
        this.endpointRegistry = endpointRegistry;
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
        String effectiveProvider = endpoint == null ? provider.provider() : endpoint.provider() == AgentEndpoint.Provider.OLLAMA ? "ollama" : "openai";
        String model = endpoint == null ? provider.model() : endpoint.model();
        String routedOperation = operationId + ":" + effectiveProvider + ":" + model;
        return switch (effectiveProvider) {
            case "ollama" -> endpoint == null ? ollama.completeWithModel(routedOperation, prompt, parser, model)
                    : new RemoteOllamaGmProvider(HttpClient.newHttpClient(), endpoint.baseUrl(), model, defaults.timeout()).complete(routedOperation, prompt, parser);
            case "openai" -> new OpenAiGmProvider(HttpClient.newHttpClient(), endpoint == null ? defaults.baseUrl() : endpoint.baseUrl(), endpoint == null ? defaults.apiKey() : System.getenv(endpoint.secretEnvironmentVariable()),
                    model, provider.reasoning(), defaults.timeout()).complete(routedOperation, prompt, parser);
            default -> throw new IllegalArgumentException("unsupported GM provider: " + provider.provider());
        };
    }
}
