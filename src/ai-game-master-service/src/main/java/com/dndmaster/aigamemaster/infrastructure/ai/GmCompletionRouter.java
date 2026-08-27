package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.net.http.HttpClient;
import java.util.Objects;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes each turn to the provider locked on its session binding. */
public final class GmCompletionRouter implements GmCompletionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GmCompletionRouter.class);
    private final SpringAiChatAdapter ollama;
    private final GmProviderProperties defaults;
    private final AgentEndpointRegistry endpointRegistry;
    private final String codexExecutable;
    private final Path codexWorkDirectory;
    private final Duration codexTimeout;
    private final CodexAppServerClient codexAppServer;
    private final GmProviderSelectionResolver selectionResolver;

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
        this.codexAppServer = CodexAppServerClient.shared(codexExecutable, codexWorkDirectory, codexTimeout, new com.fasterxml.jackson.databind.ObjectMapper());
        this.selectionResolver = endpointRegistry == null ? null : new GmProviderSelectionResolver(endpointRegistry);
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
            case "codex-cli" -> parser.parse(codexAppServer.complete(routedOperation, prompt, model));
            default -> throw new IllegalArgumentException("unsupported GM provider: " + provider.provider());
        };
    }

    @Override
    public <T> GmCompletionResult<T> completeWithSelection(
            String operationId, String prompt, StructuredResponseParser<T> parser,
            RequestedGmProviderSelection requested) {
        if (selectionResolver == null) throw new GmProviderSelectionUnresolvedException(requested);
        GmProviderSelectionResolver.EndpointResolution resolution = selectionResolver.resolveEndpoint(requested);
        EffectiveGmProviderSelection effective = resolution.effectiveSelection();
        if (!requested.provider().equals(effective.provider()) || !requested.model().equals(effective.model())) {
            LOGGER.warn("gm_provider_selection_mismatch requestedProvider={} requestedModel={} effectiveProvider={} effectiveModel={} endpointId={} endpointVersion={}",
                    safe(requested.provider()), safe(requested.model()), safe(effective.provider()), safe(effective.model()),
                    effective.endpointId(), effective.endpointVersion());
        }
        T response = completeResolved(operationId, prompt, parser, resolution.endpoint(), effective);
        return new GmCompletionResult<>(response, effective);
    }

    @Override
    public <T> GmCandidateLifecycleResult<T> completeWithOneRepair(
            String operationId, String prompt, java.util.function.Function<String, String> repairPrompt,
            StructuredResponseParser<T> parser, RequestedGmProviderSelection requested) {
        if (selectionResolver == null) throw new GmProviderSelectionUnresolvedException(requested);
        GmProviderSelectionResolver.EndpointResolution resolution = selectionResolver.resolveEndpoint(requested);
        EffectiveGmProviderSelection effective = resolution.effectiveSelection();
        java.util.concurrent.atomic.AtomicReference<String> raw = new java.util.concurrent.atomic.AtomicReference<>("");
        StructuredResponseParser<T> capturingParser = json -> {
            raw.set(json == null ? "" : json);
            return parser.parse(json);
        };
        try {
            T response = completeResolved(operationId, prompt, capturingParser, resolution.endpoint(), effective);
            return new GmCandidateLifecycleResult<>(new GmCompletionResult<>(response, effective), 1);
        } catch (ProviderMalformedResponseException malformed) {
            T repaired = completeResolved(operationId + ":repair", repairPrompt.apply(raw.get()),
                    capturingParser, resolution.endpoint(), effective);
            return new GmCandidateLifecycleResult<>(new GmCompletionResult<>(repaired, effective), 2);
        }
    }

    private <T> T completeResolved(String operationId, String prompt, StructuredResponseParser<T> parser,
                                   AgentEndpoint endpoint, EffectiveGmProviderSelection effective) {
        return switch (effective.provider()) {
            case "ollama" -> new RemoteOllamaGmProvider(HttpClient.newHttpClient(), endpoint.baseUrl(), effective.model(), defaults.timeout())
                    .complete(operationId + ":ollama:" + effective.model(), prompt, parser);
            case "openai" -> new OpenAiGmProvider(HttpClient.newHttpClient(), endpoint.baseUrl(),
                    endpoint.secretEnvironmentVariable() == null ? defaults.apiKey() : System.getenv(endpoint.secretEnvironmentVariable()),
                    effective.model(), effective.reasoning(), defaults.timeout()).complete(operationId + ":openai:" + effective.model(), prompt, parser);
            case "codex-cli" -> parser.parse(codexAppServer.complete(operationId + ":codex-cli:" + effective.model(), prompt, effective.model()));
            default -> throw new IllegalArgumentException("unsupported GM provider: " + effective.provider());
        };
    }

    private static String safe(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9._:-]", "_"); }
}
