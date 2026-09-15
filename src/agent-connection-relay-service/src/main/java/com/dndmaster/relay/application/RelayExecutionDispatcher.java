package com.dndmaster.relay.application;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
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

    public RelayExecutionDispatcher(String instanceId, ConnectionLocationLookup locations, LocalConnectionExecutor local,
                                    OwnedInstanceClient remote, RelayMetrics metrics, Duration timeout) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.locations = Objects.requireNonNull(locations);
        this.local = Objects.requireNonNull(local);
        this.remote = Objects.requireNonNull(remote);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override public Mono<RelayExecutionResult> execute(RelayExecutionRequest request) {
        long started = System.nanoTime();
        long requestBytes = utf8Bytes(request);
        return locations.find(request.soloPlayerId())
                .flatMap(found -> found.<Mono<RelayExecutionResult>>map(lease -> instanceId.equals(lease.instanceId())
                        ? local.execute(request) : remote.execute(lease.internalAddress(), request))
                        .orElseGet(() -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION))))
                .timeout(timeout)
                .onErrorResume(TimeoutException.class, ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT)))
                .onErrorResume(ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE)))
                .doOnNext(result -> {
                    metrics.finished(requestBytes, result.content().getBytes(StandardCharsets.UTF_8).length,
                            Duration.ofNanos(System.nanoTime() - started), result.failureType());
                    if (result.success()) log.info("relay execution completed requestId={} routeInstance={}", request.requestId(), instanceId);
                    else log.warn("relay execution failed requestId={} failureType={}", request.requestId(), result.failureType());
                });
    }

    private static long utf8Bytes(RelayExecutionRequest request) {
        long total = bytes(request.prompt()) + bytes(request.requestId()) + bytes(request.operationId()) + bytes(request.model())
                + bytes(request.reasoning()) + bytes(request.outputFormat());
        for (String image : request.imageInputs()) total += bytes(image);
        return total;
    }
    private static int bytes(String value) { return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length; }
}
