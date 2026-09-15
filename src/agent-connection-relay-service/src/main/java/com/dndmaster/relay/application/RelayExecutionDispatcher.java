package com.dndmaster.relay.application;

import java.time.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class RelayExecutionDispatcher implements ExecutionService {
    private static final Logger log = LoggerFactory.getLogger(RelayExecutionDispatcher.class);
    private final String instanceId;
    private final ConnectionLocationLookup locations;
    private final LocalConnectionExecutor local;
    private final OwnedInstanceClient remote;
    private final RelayMetrics metrics;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public RelayExecutionDispatcher(String instanceId, ConnectionLocationLookup locations, LocalConnectionExecutor local,
                                    OwnedInstanceClient remote, RelayMetrics metrics, Duration timeout) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.locations = Objects.requireNonNull(locations);
        this.local = Objects.requireNonNull(local);
        this.remote = Objects.requireNonNull(remote);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeout = Objects.requireNonNull(timeout);
        this.objectMapper = new ObjectMapper();
    }

    @Override public Mono<RelayExecutionResult> execute(RelayExecutionRequest request) {
        final RelayExecutionRequest routedRequest = request.deadlineEpochMillis() == 0
                ? request.withDeadline(System.currentTimeMillis() + timeout.toMillis()) : request;
        long started = System.nanoTime();
        long requestBytes = utf8Bytes(routedRequest);
        var owningInstance = new AtomicReference<>("none");
        Duration remaining = Duration.ofMillis(routedRequest.deadlineEpochMillis() - System.currentTimeMillis());
        if (remaining.isZero() || remaining.isNegative()) {
            var result = RelayExecutionResult.failure(routedRequest.requestId(), RelayFailureType.TIMEOUT);
            long responseBytes = jsonBytes(result);
            metrics.finished(requestBytes, responseBytes, Duration.ZERO, RelayFailureType.TIMEOUT);
            log.warn("relay execution failed requestId={} fromInstance={} owningInstance=none requestBytes={} responseBytes={} durationMs=0 failureType=TIMEOUT",
                    routedRequest.requestId(), instanceId, requestBytes, responseBytes);
            return Mono.just(result);
        }
        return locations.find(routedRequest.soloPlayerId())
                .flatMap(found -> found.<Mono<RelayExecutionResult>>map(lease -> {
                    owningInstance.set(lease.instanceId());
                    var targetedRequest = routedRequest.withConnectionId(lease.connectionId());
                    return instanceId.equals(lease.instanceId()) ? local.execute(targetedRequest) : remote.execute(lease.internalAddress(), targetedRequest);
                })
                        .orElseGet(() -> Mono.just(RelayExecutionResult.failure(routedRequest.requestId(), RelayFailureType.NO_CONNECTION))))
                .timeout(remaining.compareTo(timeout) < 0 ? remaining : timeout)
                .onErrorResume(TimeoutException.class, ignored -> Mono.just(RelayExecutionResult.failure(routedRequest.requestId(), RelayFailureType.TIMEOUT)))
                .onErrorResume(ignored -> Mono.just(RelayExecutionResult.failure(routedRequest.requestId(), RelayFailureType.REMOTE_FAILURE)))
                .map(result -> routedRequest.requestId().equals(result.requestId()) ? result
                        : RelayExecutionResult.failure(routedRequest.requestId(), RelayFailureType.REMOTE_FAILURE))
                .doOnNext(result -> {
                    long responseBytes = jsonBytes(result);
                    long durationMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    metrics.finished(requestBytes, responseBytes, Duration.ofMillis(durationMillis), result.failureType());
                    if (result.success()) log.info("relay execution completed requestId={} fromInstance={} owningInstance={} requestBytes={} responseBytes={} durationMs={}",
                            routedRequest.requestId(), instanceId, owningInstance.get(), requestBytes, responseBytes, durationMillis);
                    else log.warn("relay execution failed requestId={} fromInstance={} owningInstance={} requestBytes={} responseBytes={} durationMs={} failureType={}",
                            routedRequest.requestId(), instanceId, owningInstance.get(), requestBytes, responseBytes, durationMillis, result.failureType());
                });
    }

    private long utf8Bytes(RelayExecutionRequest request) {
        return jsonBytes(request);
    }
    private int jsonBytes(Object value) {
        try { return objectMapper.writeValueAsBytes(value).length; }
        catch (Exception failure) { throw new IllegalArgumentException("relay payload cannot be encoded", failure); }
    }
}
