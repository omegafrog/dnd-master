package com.dndmaster.relay.api;

import com.dndmaster.relay.application.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.slf4j.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
public final class InternalExecutionController {
    private static final Logger log = LoggerFactory.getLogger(InternalExecutionController.class);
    private final ExecutionService dispatcher;
    private final LocalConnectionExecutor local;
    private final byte[] internalToken;
    private final byte[] relayPeerToken;
    private final RelayMetrics metrics;
    private final String instanceId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalExecutionController(ExecutionService dispatcher, String internalToken) {
        this(dispatcher, request -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION)),
                internalToken, internalToken, RelayMetrics.noop(), "relay-instance");
    }
    public InternalExecutionController(ExecutionService dispatcher, LocalConnectionExecutor local,
            String internalToken, String relayPeerToken) {
        this(dispatcher, local, internalToken, relayPeerToken, RelayMetrics.noop(), "relay-instance");
    }
    @Autowired
    public InternalExecutionController(ExecutionService dispatcher, LocalConnectionExecutor local,
            @Value("${relay.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            @Value("${relay.peer-token:${RELAY_INTERNAL_SERVICE_TOKEN:${INTERNAL_SERVICE_TOKEN:}}}") String relayPeerToken,
            RelayMetrics metrics, @Value("${relay.instance-id:relay-instance}") String instanceId) {
        this.dispatcher = dispatcher; this.local = local;
        this.metrics = metrics; this.instanceId = instanceId;
        if (internalToken == null || internalToken.isBlank()) throw new IllegalStateException("INTERNAL_SERVICE_TOKEN is required");
        if (relayPeerToken == null || relayPeerToken.isBlank()) throw new IllegalStateException("RELAY_INTERNAL_SERVICE_TOKEN is required");
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
        this.relayPeerToken = relayPeerToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/executions")
    Mono<RelayExecutionResult> execute(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                       @RequestBody RelayExecutionRequest request) {
        authenticate(token); return dispatcher.execute(request);
    }

    @PostMapping("/internal/owned-executions")
    Mono<RelayExecutionResult> executeOwned(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                            @RequestHeader(value = "X-Relay-Instance-Id", required = false) String relayInstanceId,
                                            @RequestHeader(value = "X-Internal-Caller", required = false) String internalCaller,
                                            @RequestBody RelayExecutionRequest request) {
        authenticateRelay(token);
        if (!"agent-connection-relay-service".equals(internalCaller) || relayInstanceId == null || relayInstanceId.isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "relay caller identity is required");
        long started = System.nanoTime();
        long requestBytes = jsonBytes(request);
        return local.execute(request).doOnNext(result -> {
            long responseBytes = jsonBytes(result);
            Duration duration = Duration.ofNanos(System.nanoTime() - started);
            metrics.finished(requestBytes, responseBytes, duration, result.failureType());
            log.info("owned relay execution completed requestId={} instanceId={} requestBytes={} responseBytes={} durationMs={} failureType={}",
                    request.requestId(), instanceId, requestBytes, responseBytes, duration.toMillis(), result.failureType());
        });
    }

    private void authenticate(String token) {
        byte[] supplied = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalToken, supplied)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
    }
    private void authenticateRelay(String token) {
        byte[] supplied = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(relayPeerToken, supplied)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid relay peer token");
    }
    private int jsonBytes(Object value) {
        try { return objectMapper.writeValueAsBytes(value).length; }
        catch (Exception failure) { throw new IllegalArgumentException("relay payload cannot be encoded", failure); }
    }
}
