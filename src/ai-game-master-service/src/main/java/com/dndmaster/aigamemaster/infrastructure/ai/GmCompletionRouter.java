package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes each turn to the provider locked on its session binding. */
public final class GmCompletionRouter implements GmCompletionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GmCompletionRouter.class);
    private final SpringAiChatAdapter ollama;
    private final GmProviderProperties defaults;
    private final AgentEndpointRegistry endpointRegistry;
    private final AiExecutionPort aiExecutionPort;
    private final GmProviderSelectionResolver selectionResolver;

    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults) {
        this(ollama, defaults, null);
    }
    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults, AgentEndpointRegistry endpointRegistry) {
        this(ollama, defaults, endpointRegistry, new RemoteAiExecutionPort());
    }
    public GmCompletionRouter(SpringAiChatAdapter ollama, GmProviderProperties defaults, AgentEndpointRegistry endpointRegistry,
                              AiExecutionPort aiExecutionPort) {
        this.ollama = Objects.requireNonNull(ollama);
        this.defaults = Objects.requireNonNull(defaults);
        this.endpointRegistry = endpointRegistry;
        this.aiExecutionPort = Objects.requireNonNull(aiExecutionPort);
        this.selectionResolver = endpointRegistry == null ? null : new GmProviderSelectionResolver(endpointRegistry);
    }

    @Override
    public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
        return complete(operationId, prompt, parser,
                new GmProviderRequest(defaults.provider(), defaults.model(), defaults.reasoning()));
    }

    @Override
    public <T> T complete(String operationId, GmPrompt prompt, StructuredResponseParser<T> parser) {
        GmProviderRequest provider = new GmProviderRequest(defaults.provider(), defaults.model(), defaults.reasoning());
        AgentEndpoint endpoint = endpointRegistry == null ? null : endpointRegistry.active();
        String effectiveProvider = endpoint == null ? provider.provider() : switch (endpoint.provider()) {
            case OLLAMA -> "ollama";
            case OPENAI_COMPATIBLE -> "openai";
            case CODEX_CLI -> "codex-cli";
        };
        String model = endpoint == null ? provider.model() : endpoint.model();
        String routedOperation = operationId + ":" + effectiveProvider + ":" + model;
        return switch (effectiveProvider) {
            case "ollama" -> endpoint == null
                    ? ollama.complete(routedOperation, prompt, parser)
                    : new RemoteOllamaGmProvider(HttpClient.newHttpClient(), endpoint.baseUrl(), model, defaults.timeout())
                            .complete(routedOperation, prompt, parser);
            case "openai" -> new OpenAiGmProvider(HttpClient.newHttpClient(), endpoint == null ? defaults.baseUrl() : endpoint.baseUrl(),
                    endpoint == null ? defaults.apiKey() : System.getenv(endpoint.secretEnvironmentVariable()),
                    model, provider.reasoning(), defaults.timeout()).complete(routedOperation, prompt, parser);
            case "codex-cli" -> requiresServerConfirmedIdentity();
            default -> throw new IllegalArgumentException("unsupported GM provider: " + provider.provider());
        };
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
            case "codex-cli" -> requiresServerConfirmedIdentity();
            default -> throw new IllegalArgumentException("unsupported GM provider: " + provider.provider());
        };
    }

    @Override
    public <T> T complete(UUID soloPlayerId, String operationId, GmPrompt prompt,
                          StructuredResponseParser<T> parser) {
        AgentEndpoint endpoint = endpointRegistry == null ? null : endpointRegistry.active();
        String provider = endpoint == null ? defaults.provider() : switch (endpoint.provider()) {
            case OLLAMA -> "ollama";
            case OPENAI_COMPATIBLE -> "openai";
            case CODEX_CLI -> "codex-cli";
        };
        if (!"codex-cli".equals(provider)) return complete(operationId, prompt, parser);
        String model = endpoint == null ? defaults.model() : endpoint.model();
        String reasoning = defaults.reasoning();
        return parser.parse(aiExecutionPort.execute(new AiExecutionRequest(soloPlayerId, operationId, operationId,
                prompt.text(), model, reasoning, "TEXT", null, prompt.imageDataUri())).requireFinalText());
    }

    @Override
    public <T> T complete(UUID soloPlayerId, String operationId, String prompt,
                           StructuredResponseParser<T> parser) {
        return complete(soloPlayerId, operationId, new GmPrompt(prompt), parser);
    }

    @Override
    public <T> T complete(UUID soloPlayerId, String operationId, GmPrompt prompt,
                          StructuredResponseParser<T> parser, com.fasterxml.jackson.databind.JsonNode outputSchema) {
        AgentEndpoint endpoint = endpointRegistry == null ? null : endpointRegistry.active();
        String provider = endpoint == null ? defaults.provider() : switch (endpoint.provider()) {
            case OLLAMA -> "ollama";
            case OPENAI_COMPATIBLE -> "openai";
            case CODEX_CLI -> "codex-cli";
        };
        if (!"codex-cli".equals(provider)) return complete(soloPlayerId, operationId, prompt, parser);
        String model = endpoint == null ? defaults.model() : endpoint.model();
        String reasoning = defaults.reasoning();
        return parser.parse(aiExecutionPort.execute(new AiExecutionRequest(soloPlayerId, operationId, operationId,
                prompt.text(), model, reasoning, "JSON", outputSchema, prompt.imageDataUri())).requireFinalText());
    }

    @Override
    public <T> GmCompletionResult<T> completeWithSelection(UUID soloPlayerId,
            String operationId, String prompt, StructuredResponseParser<T> parser,
            RequestedGmProviderSelection requested) {
        if (selectionResolver == null) throw new GmProviderSelectionUnresolvedException(requested);
        GmProviderSelectionResolver.EndpointResolution resolution = selectionResolver.resolveEndpoint(requested);
        EffectiveGmProviderSelection effective = resolution.effectiveSelection();
        T response = completeResolved(soloPlayerId, operationId, prompt, parser, resolution.endpoint(), effective);
        return new GmCompletionResult<>(response, effective);
    }

    @Override
    public <T> GmCandidateLifecycleResult<T> completeWithOneRepair(
            UUID soloPlayerId, String operationId, String prompt, java.util.function.Function<GmRepairContext, String> repairPrompt,
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
            T response = completeResolved(soloPlayerId, operationId, prompt, capturingParser, resolution.endpoint(), effective);
            return new GmCandidateLifecycleResult<>(new GmCompletionResult<>(response, effective), 1);
        } catch (ProviderMalformedResponseException malformed) {
            LOGGER.warn("gm_candidate_validation_failed stage=INITIAL_CANDIDATE_VALIDATION code=MALFORMED_JSON message={}", malformed.getMessage());
            T repaired = completeResolved(soloPlayerId, operationId + ":repair", repairPrompt.apply(new GmRepairContext(raw.get(), List.of(
                            new GmCandidateViolation("MALFORMED_JSON", "candidate", malformed.getMessage())))),
                    capturingParser, resolution.endpoint(), effective);
            return new GmCandidateLifecycleResult<>(new GmCompletionResult<>(repaired, effective), 2);
        } catch (GmCandidateValidationException invalid) {
            LOGGER.warn("gm_candidate_validation_failed stage=INITIAL_CANDIDATE_VALIDATION violations={}", invalid.violations());
            T repaired = completeResolved(soloPlayerId, operationId + ":repair", repairPrompt.apply(new GmRepairContext(raw.get(), invalid.violations())),
                    capturingParser, resolution.endpoint(), effective);
            return new GmCandidateLifecycleResult<>(new GmCompletionResult<>(repaired, effective), 2);
        }
    }

    @Override
    public <T> GmCandidateLifecycleResult<T> completeWithOneRepair(UUID soloPlayerId, String operationId, String prompt,
            java.util.function.Function<GmRepairContext, String> repairPrompt,
            StructuredResponseContract<T> contract, RequestedGmProviderSelection requested) {
        if (selectionResolver == null) throw new GmProviderSelectionUnresolvedException(requested);
        var resolution = selectionResolver.resolveEndpoint(requested);
        var effective = resolution.effectiveSelection();
        java.util.concurrent.atomic.AtomicReference<String> raw = new java.util.concurrent.atomic.AtomicReference<>("");
        StructuredResponseParser<T> parser = json -> { raw.set(json == null ? "" : json); return contract.parser().parse(json); };
        try { return new GmCandidateLifecycleResult<>(new GmCompletionResult<>(completeResolved(soloPlayerId, operationId, prompt, parser, resolution.endpoint(), effective, contract.outputSchema()), effective), 1); }
        catch (ProviderMalformedResponseException | GmCandidateValidationException failure) {
            var violations = failure instanceof GmCandidateValidationException v ? v.violations() : java.util.List.of(new GmCandidateViolation("MALFORMED_JSON", "candidate", failure.getMessage()));
            T repaired = completeResolved(soloPlayerId, operationId + ":repair", repairPrompt.apply(new GmRepairContext(raw.get(), violations)), parser, resolution.endpoint(), effective, contract.outputSchema());
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
            case "codex-cli" -> requiresServerConfirmedIdentity();
            default -> throw new IllegalArgumentException("unsupported GM provider: " + effective.provider());
        };
    }
    private <T> T completeResolved(UUID soloPlayerId, String operationId, String prompt, StructuredResponseParser<T> parser,
                                   AgentEndpoint endpoint, EffectiveGmProviderSelection effective) {
        if ("codex-cli".equals(effective.provider())) {
            return parser.parse(aiExecutionPort.execute(new AiExecutionRequest(soloPlayerId, operationId, operationId,
                    prompt, effective.model(), effective.reasoning(), "TEXT", null, "")).requireFinalText());
        }
        return completeResolved(operationId, prompt, parser, endpoint, effective);
    }
    private <T> T completeResolved(String operationId, String prompt, StructuredResponseParser<T> parser,
                                   AgentEndpoint endpoint, EffectiveGmProviderSelection effective, com.fasterxml.jackson.databind.JsonNode schema) {
        if ("codex-cli".equals(effective.provider())) return requiresServerConfirmedIdentity();
        return completeResolved(operationId, prompt, parser, endpoint, effective);
    }
    private <T> T completeResolved(UUID soloPlayerId, String operationId, String prompt, StructuredResponseParser<T> parser,
                                   AgentEndpoint endpoint, EffectiveGmProviderSelection effective, com.fasterxml.jackson.databind.JsonNode schema) {
        if ("codex-cli".equals(effective.provider())) {
            return parser.parse(aiExecutionPort.execute(new AiExecutionRequest(soloPlayerId, operationId, operationId,
                    prompt, effective.model(), effective.reasoning(), "JSON", schema, "")).requireFinalText());
        }
        return completeResolved(operationId, prompt, parser, endpoint, effective, schema);
    }

    private static String safe(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9._:-]", "_"); }

    private static <T> T requiresServerConfirmedIdentity() {
        throw new IllegalStateException("Codex execution requires a server-confirmed Solo Player ID through the AI execution port");
    }
}
