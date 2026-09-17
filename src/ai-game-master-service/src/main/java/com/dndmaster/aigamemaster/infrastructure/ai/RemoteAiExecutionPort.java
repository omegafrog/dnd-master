package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
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
        AiExecutionResult result = executeInternal(request);
        String outcome = result instanceof AiExecutionSuccess ? "success" : ((AiExecutionFailure) result).reason().name();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        if (metrics != null) {
            Counter.builder("ai.execution.count").tag("implementation", "remote-relay").tag("result", outcome).register(metrics).increment();
            Timer.builder("ai.execution.duration").tag("implementation", "remote-relay").tag("result", outcome).register(metrics).record(elapsed);
        }
        log.info("remote AI execution completed requestId={} implementation=remote-relay result={} durationMs={}",
                AiCallObservability.safe(request.requestId()), outcome, elapsed.toMillis());
        return result;
    }
    private AiExecutionResult executeInternal(AiExecutionRequest request) {
        if (endpoint == null) return failure(AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE);
        try {
            var body = new RelayRequest(request.soloPlayerId(), request.requestId(), request.workId(), request.completedPrompt(),
                    request.model(), request.reasoning(), request.outputFormat(), request.outputSchema(),
                    request.imageDataUri().isBlank() ? List.of() : List.of(request.imageDataUri()),
                    System.currentTimeMillis() + timeout.toMillis());
            var httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken).POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) return failure(AiExecutionFailure.Reason.DELIVERY_FAILED);
            var result = mapper.readValue(response.body(), RelayResult.class);
            if (!request.requestId().equals(result.requestId())) return failure(AiExecutionFailure.Reason.DELIVERY_FAILED);
            if (result.failureType() == null) return new AiExecutionSuccess(result.content());
            return failure(switch (result.failureType()) {
                case "NO_CONNECTION" -> AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE;
                case "CONNECTION_LOST" -> AiExecutionFailure.Reason.CONNECTION_LOST;
                case "TIMEOUT" -> AiExecutionFailure.Reason.TIMEOUT;
                default -> AiExecutionFailure.Reason.DELIVERY_FAILED;
            });
        } catch (java.net.http.HttpTimeoutException failure) {
            return failure(AiExecutionFailure.Reason.TIMEOUT);
        } catch (Exception failure) {
            return failure(AiExecutionFailure.Reason.DELIVERY_FAILED);
        }
    }
    private static AiExecutionFailure failure(AiExecutionFailure.Reason reason) { return new AiExecutionFailure(reason, reason.name()); }
    private record RelayRequest(java.util.UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs,
                                long deadlineEpochMillis) {}
    private record RelayResult(String requestId, String content, String failureType) {}
}
