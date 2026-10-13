package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.dndmaster.aigamemaster.application.ai.AiExecutionUsage;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import io.micrometer.core.instrument.*;
import org.slf4j.*;

/** Sends the server-completed prompt to the authenticated connection relay API. */
public final class RemoteAiExecutionPort implements AiExecutionPort {
    private static final Logger log = LoggerFactory.getLogger(RemoteAiExecutionPort.class);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String internalToken;
    private final Duration timeout;
    private final MeterRegistry metrics;

    /** Backward-compatible non-runtime constructor used by deterministic component tests. */
    public RemoteAiExecutionPort() { this(null, new ObjectMapper(), null, "", Duration.ofSeconds(1), null); }
    public RemoteAiExecutionPort(HttpClient client, ObjectMapper mapper, URI relayBaseUri, String internalToken, Duration timeout) {
        this(client, mapper, relayBaseUri, internalToken, timeout, null);
    }
    public RemoteAiExecutionPort(HttpClient client, ObjectMapper mapper, URI relayBaseUri, String internalToken, Duration timeout, MeterRegistry metrics) {
        this.client = client; this.mapper = mapper; this.endpoint = relayBaseUri == null ? null : relayBaseUri.resolve("/internal/executions");
        this.internalToken = internalToken == null ? "" : internalToken; this.timeout = timeout; this.metrics = metrics;
    }
    @Override
    public AiExecutionResult execute(AiExecutionRequest request) {
        long started = System.nanoTime();
        log.info("remote AI execution started requestId={} implementation=remote-relay", AiCallObservability.safe(request.requestId()));
        var trace = new ExecutionTrace();
        devLog(request.requestId(), "relay_dispatch", null, null, null, trace, started, "started", "unknown");
        AiExecutionResult result = executeInternal(request, trace, started);
        String outcome = result instanceof AiExecutionSuccess ? "success" : ((AiExecutionFailure) result).reason().name();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        if (metrics != null) {
            Counter.builder("ai.execution.count").tag("implementation", "remote-relay").tag("result", outcome).register(metrics).increment();
            Timer.builder("ai.execution.duration").tag("implementation", "remote-relay").tag("result", outcome).register(metrics).record(elapsed);
        }
        if (result instanceof AiExecutionSuccess success) {
            var usage = success.usage();
            log.info("remote AI provider usage requestId={} inputTokens={} cachedInputTokens={} outputTokens={}",
                    AiCallObservability.safe(request.requestId()), usage.inputTokens(), usage.cachedInputTokens(), usage.outputTokens());
        }
        log.info("remote AI execution completed requestId={} implementation=remote-relay result={} durationMs={}",
                AiCallObservability.safe(request.requestId()), outcome, elapsed.toMillis());
        devLog(request.requestId(), "execution_complete", trace.httpStatus, null, null, trace, started, outcome, retrySafety(result));
        return result;
    }
    private AiExecutionResult executeInternal(AiExecutionRequest request, ExecutionTrace trace, long started) {
        if (endpoint == null) {
            devLog(request.requestId(), "endpoint_unavailable", null, null, null, trace, started, "failed", "safe_not_dispatched");
            return failure(AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE);
        }
        try {
            var body = new RelayRequest(request.soloPlayerId(), request.requestId(), request.workId(), request.completedPrompt(),
                    request.model(), request.reasoning(), request.outputFormat(), request.outputSchema(),
                    request.imageDataUri().isBlank() ? List.of() : List.of(request.imageDataUri()),
                    request.subtasks().stream().map(task -> new RelaySubtask(task.stageId(), task.prompt(), task.outputSchema())).toList(),
                    System.currentTimeMillis() + timeout.toMillis(), request.ragSearchContext());
            var httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken).POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            trace.dispatched = true;
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            trace.httpResponseReceived = true;
            trace.httpStatus = response.statusCode();
            if (response.statusCode() / 100 != 2) {
                log.warn("remote AI relay returned non-success status requestId={} status={}",
                        AiCallObservability.safe(request.requestId()), response.statusCode());
                devLog(request.requestId(), "relay_http_response", response.statusCode(), null, null, trace, started, "failed", "unknown");
                return failure(AiExecutionFailure.Reason.DELIVERY_FAILED);
            }
            var result = mapper.readValue(response.body(), RelayResult.class);
            if (!request.requestId().equals(result.requestId())) {
                devLog(request.requestId(), "request_id_mismatch", response.statusCode(), safeFailureType(result.failureType()), null,
                        trace, started, "failed", "unknown");
                return failure(AiExecutionFailure.Reason.DELIVERY_FAILED);
            }
            if (result.failureType() == null) return new AiExecutionSuccess(result.content(),
                    result.usage() == null ? AiExecutionUsage.unknown() : result.usage());
            devLog(request.requestId(), "relay_result", response.statusCode(), safeFailureType(result.failureType()), null,
                    trace, started, "failed", retrySafety(result.failureType()));
            return failure(switch (result.failureType()) {
                case "NO_CONNECTION" -> AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE;
                case "CONNECTION_REQUIRED" -> AiExecutionFailure.Reason.CONNECTION_REQUIRED;
                case "REAUTH_REQUIRED" -> AiExecutionFailure.Reason.REAUTH_REQUIRED;
                case "CONNECTION_LOST" -> AiExecutionFailure.Reason.CONNECTION_LOST;
                case "TIMEOUT" -> AiExecutionFailure.Reason.TIMEOUT;
                case "OUTPUT_LIMIT" -> AiExecutionFailure.Reason.OUTPUT_LIMIT;
                default -> AiExecutionFailure.Reason.DELIVERY_FAILED;
            });
        } catch (java.net.http.HttpTimeoutException failure) {
            devLog(request.requestId(), "timeout", trace.httpStatus, null, failure.getClass().getSimpleName(),
                    trace, started, "failed", trace.dispatched ? "unknown" : "safe_not_dispatched");
            return failure(AiExecutionFailure.Reason.TIMEOUT);
        } catch (Exception failure) {
            devLog(request.requestId(), "exception", trace.httpStatus, null, failure.getClass().getSimpleName(),
                    trace, started, "failed", trace.dispatched ? "unknown" : "safe_not_dispatched");
            return failure(AiExecutionFailure.Reason.DELIVERY_FAILED);
        }
    }
    private static boolean runtimeDiagnosticsEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
    }
    private static String safeFailureType(String failureType) {
        if (failureType == null) return "none";
        return switch (failureType) {
            case "NO_CONNECTION", "CONNECTION_REQUIRED", "REAUTH_REQUIRED", "CONNECTION_LOST", "TIMEOUT", "OUTPUT_LIMIT" -> failureType;
            default -> "UNKNOWN";
        };
    }
    private static String retrySafety(String failureType) {
        return "NO_CONNECTION".equals(failureType) ? "safe_no_connection" : "unknown";
    }
    private static String retrySafety(AiExecutionResult result) {
        if (result instanceof AiExecutionSuccess) return "unsafe_already_completed";
        return ((AiExecutionFailure) result).reason() == AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE
                ? "safe_no_connection" : "unknown";
    }
    private static void devLog(String requestId, String stage, Integer httpStatus, String relayFailureType,
                               String exceptionClass, ExecutionTrace trace, long started, String outcome, String retrySafety) {
        if (!runtimeDiagnosticsEnabled()) return;
        String dispatchState = trace.httpResponseReceived ? "http_response_received" : trace.dispatched ? "dispatched_outcome_unknown" : "not_dispatched";
        log.info("dev_remote_ai_execution outcome={} requestId={} stage={} httpStatus={} relayFailureType={} exceptionClass={} dispatchState={} dispatched={} relayHttpResponseReceived={} elapsedMs={} retrySafety={}",
                outcome, AiCallObservability.safe(requestId), stage, httpStatus == null ? "none" : httpStatus,
                relayFailureType == null ? "none" : relayFailureType, exceptionClass == null ? "none" : exceptionClass,
                dispatchState, trace.dispatched, trace.httpResponseReceived,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), retrySafety);
    }
    private static final class ExecutionTrace {
        private boolean dispatched;
        private boolean httpResponseReceived;
        private Integer httpStatus;
    }
    private static AiExecutionFailure failure(AiExecutionFailure.Reason reason) { return new AiExecutionFailure(reason, reason.name()); }
    private record RelayRequest(java.util.UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs,
                                List<RelaySubtask> subtasks, long deadlineEpochMillis, JsonNode ragSearchContext) {}
    private record RelaySubtask(String stageId, String prompt, JsonNode outputSchema) {}
    private record RelayResult(String requestId, String content, String failureType, AiExecutionUsage usage) {}
}
