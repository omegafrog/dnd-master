package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationAgentPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationAgentFailureException;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Sends selected, source-identified Storybook excerpts to the semantic scenario compiler. */
public final class HttpScenarioCompilationAgentPort implements ScenarioCompilationAgentPort {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HttpScenarioCompilationAgentPort.class);
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpScenarioCompilationAgentPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override
    public ScenarioCompilationAgentResult compile(ScenarioCompilationAgentRequest request) {
        return compile(new UUID(0, 0), request);
    }

    @Override
    public ScenarioCompilationAgentResult compile(UUID soloPlayerId, ScenarioCompilationAgentRequest request) {
        try {
            String body = mapper.writeValueAsString(new CompilationRequest(soloPlayerId,
                    request.operationKey(), request.storybookContext()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/gm/scenario-compilation"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw decodeFailure(response.statusCode(), response.body(), request.operationKey());
            }
            CompilationResponse result = mapper.readValue(response.body(), CompilationResponse.class);
            if ("BLOCKED".equalsIgnoreCase(result.status())) {
                return new ScenarioCompilationAgentResult(ScenarioCompilationAgentResult.Status.BLOCKED, null,
                        result.diagnostics());
            }
            if (!"READY".equalsIgnoreCase(result.status()) || result.scenarioModel() == null) {
                throw new IllegalStateException("scenario compilation agent returned an invalid result");
            }
            Map<String, Object> values = new java.util.HashMap<>(result.scenarioModel());
            values.putIfAbsent("schemaVersion", 1);
            normalizeSchemaVersion(values);
            normalizeStartingSituation(values);
            normalizeElementTypes(values);
            for (String field : List.of("actors", "locations", "objectives", "revelations", "encounters",
                    "relationships", "resolutionCriteria")) values.putIfAbsent(field, List.of());
            values.putIfAbsent("startingSituation", "");
            ScenarioModel model;
            try {
                model = mapper.convertValue(values, ScenarioModel.class);
            } catch (IllegalArgumentException invalidModel) {
                ScenarioModelDeserializationDiagnostic diagnostic = scenarioModelDiagnostic(invalidModel);
                if (diagnostic != null && DevelopmentDiagnostics.enabled()) {
                    LOGGER.warn("dev_scenario_compilation stage=output_validation operationKey={} code={} path={} expected={} actual={} recoveryAction=reject_response outcome=failed",
                            request.operationKey(), diagnostic.code(), diagnostic.path(), diagnostic.expected(), diagnostic.actual());
                }
                throw invalidModel;
            }
            return new ScenarioCompilationAgentResult(ScenarioCompilationAgentResult.Status.READY, model, result.diagnostics());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("scenario compilation agent request interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof ScenarioCompilationAgentFailureException failure) throw failure;
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("scenario compilation agent request failed", exception);
        }
    }

    static ScenarioCompilationAgentFailureException decodeFailure(int status, String body, String fallbackCorrelationId) {
        String code = "SCENARIO_COMPILATION_AGENT_HTTP_" + status;
        String correlationId = fallbackCorrelationId;
        String rootCauseClass = "RemoteScenarioCompilationFailure";
        boolean retryable = status >= 500;
        if (body != null && !body.isBlank()) {
            try {
                var error = new ObjectMapper().readTree(body);
                String candidateCode = error.path("code").asText("");
                String candidateCorrelation = error.path("correlationId").asText("");
                String candidateRootCauseClass = error.path("rootCauseClass").asText("");
                if (candidateCode.matches("[A-Z0-9_]{1,80}")) code = candidateCode;
                if (candidateCorrelation.matches("[A-Za-z0-9:_-]{1,120}")) correlationId = candidateCorrelation;
                if (candidateRootCauseClass.matches("[A-Z][A-Za-z0-9_$]{0,119}")) rootCauseClass = candidateRootCauseClass;
                if (error.path("retryable").isBoolean()) retryable = error.path("retryable").booleanValue();
            } catch (Exception ignored) {
                // Do not copy an unstructured response body into logs or persisted diagnostics.
            }
        }
        return new ScenarioCompilationAgentFailureException(status, code, correlationId, rootCauseClass, retryable);
    }

    static void normalizeSchemaVersion(Map<String, Object> values) {
        Object version = values.get("schemaVersion");
        if (!(version instanceof String text)) return;
        try {
            values.put("schemaVersion", new BigDecimal(text).intValueExact());
        } catch (NumberFormatException | ArithmeticException ignored) {
            // Leave invalid values intact so the ScenarioModel deserializer reports the contract error.
        }
    }

    static ScenarioModelDeserializationDiagnostic scenarioModelDiagnostic(Throwable failure) {
        JsonMappingException mappingFailure = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonMappingException mapping) {
                mappingFailure = mapping;
                break;
            }
        }
        if (mappingFailure == null) return null;

        StringBuilder path = new StringBuilder("scenarioModel");
        for (JsonMappingException.Reference reference : mappingFailure.getPath()) {
            if (reference.getFieldName() != null && reference.getFieldName().matches("[A-Za-z0-9_$]{1,80}")) {
                path.append('.').append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            } else {
                path.append("[?");
            }
        }

        String expected = mappingFailure instanceof MismatchedInputException mismatch
                && mismatch.getTargetType() != null
                ? mismatch.getTargetType().getSimpleName() : "ScenarioModel";
        String actual = "unknown";
        if (mappingFailure.getProcessor() instanceof JsonParser parser && parser.currentToken() != null) {
            actual = parser.currentToken().name();
        }
        String code = mappingFailure instanceof MismatchedInputException
                ? "SCENARIO_MODEL_TYPE_MISMATCH" : "SCENARIO_MODEL_DESERIALIZATION_FAILED";
        return new ScenarioModelDeserializationDiagnostic(code, path.toString(), expected, actual);
    }

    static void normalizeStartingSituation(Map<String, Object> values) {
        Object situation = values.get("startingSituation");
        if (!(situation instanceof Map<?, ?> details)) return;
        List<String> parts = new java.util.ArrayList<>();
        details.forEach((key, value) -> {
            String text = textValue(value);
            if (!text.isBlank()) {
                String label = key == null ? "" : key.toString().trim();
                parts.add(label.isBlank() ? text : label + ": " + text);
            }
        });
        if (!parts.isEmpty()) values.put("startingSituation", String.join("; ", parts));
    }

    private static String textValue(Object value) {
        if (value instanceof String text) return text.trim();
        if (value instanceof List<?> items) return items.stream().map(HttpScenarioCompilationAgentPort::textValue)
                .filter(text -> !text.isBlank()).collect(java.util.stream.Collectors.joining("; "));
        if (value instanceof Map<?, ?> details) {
            List<String> parts = new java.util.ArrayList<>();
            details.forEach((key, nested) -> {
                String text = textValue(nested);
                if (!text.isBlank()) parts.add((key == null ? "" : key + ": ") + text);
            });
            return String.join("; ", parts);
        }
        return value == null ? "" : value.toString();
    }

    static void normalizeElementTypes(Map<String, Object> values) {
        Map<String, String> requiredTypes = Map.of(
                "actors", "actor",
                "locations", "location",
                "objectives", "objective",
                "revelations", "revelation",
                "encounters", "combat-scenario",
                "relationships", "relationship",
                "resolutionCriteria", "resolution");
        requiredTypes.forEach((field, requiredType) -> {
            Object rawElements = values.get(field);
            if (!(rawElements instanceof List<?> elements)) return;
            List<Object> normalized = new java.util.ArrayList<>(elements.size());
            for (Object rawElement : elements) {
                if (!(rawElement instanceof Map<?, ?> element)) {
                    normalized.add(rawElement);
                    continue;
                }
                Map<String, Object> copy = new java.util.HashMap<>();
                element.forEach((key, value) -> {
                    if (key instanceof String name) copy.put(name, value);
                });
                Object type = copy.get("type");
                if (!(type instanceof String text) || text.isBlank()) {
                    Object declaredType = copy.get("elementType");
                    copy.put("type", declaredType instanceof String text && !text.isBlank() ? text : requiredType);
                }
                normalized.add(copy);
            }
            values.put(field, normalized);
        });
    }

    private record CompilationRequest(UUID soloPlayerId, String operationKey, String storybookContext) { }
    record ScenarioModelDeserializationDiagnostic(String code, String path, String expected, String actual) { }
    private record CompilationResponse(String status, Map<String, Object> scenarioModel, List<String> diagnostics) {
        private CompilationResponse {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
}
