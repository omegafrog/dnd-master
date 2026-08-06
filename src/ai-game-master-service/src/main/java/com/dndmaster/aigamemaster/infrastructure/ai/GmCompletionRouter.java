package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import java.net.http.HttpClient;
import java.util.Objects;

/** Routes each turn to the provider locked on its session binding. */
public final class GmCompletionRouter implements GmCompletionAdapter {
    private final SpringAiChatAdapter ollama;
    private final GmProviderProperties defaults;

    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults) {
        this.ollama = Objects.requireNonNull(ollama);
        this.defaults = Objects.requireNonNull(defaults);
    }

    @Override
    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
        return complete(operationId, prompt, parser,
                new GmProviderRequest(defaults.provider(), defaults.model(), defaults.reasoning()));
    }

    @Override
    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser,
                          GmProviderRequest provider) {
        String routedOperation = operationId + ":" + provider.provider() + ":" + provider.model();
        return switch (provider.provider()) {
            case "ollama" -> ollama.completeWithModel(routedOperation, prompt, parser, provider.model());
            case "openai" -> new OpenAiGmProvider(HttpClient.newHttpClient(), defaults.baseUrl(), defaults.apiKey(),
                    provider.model(), provider.reasoning(), defaults.timeout()).complete(routedOperation, prompt, parser);
            default -> throw new IllegalArgumentException("unsupported GM provider: " + provider.provider());
        };
    }
}
