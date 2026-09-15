package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import reactor.core.publisher.Mono;

public final class LocalConnectionManager implements LocalConnectionExecutor {
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
    private final ConnectionLeaseService leases;
    private final RequestCompletionRegistry completions;
    private final RelayMetrics metrics;
    private final Duration executionTimeout;

    public LocalConnectionManager(ConnectionLeaseService leases, RequestCompletionRegistry completions,
                                  RelayMetrics metrics, Duration executionTimeout) {
        this.leases = leases; this.completions = completions; this.metrics = metrics; this.executionTimeout = executionTimeout;
    }
    public Mono<Void> connect(ConnectionLocationLease lease, Duration ttl, AgentConnectionTransport transport) {
        var connection = new Connection(lease.connectionId(), transport);
        return leases.claim(lease, ttl).then(Mono.fromRunnable(() -> {
            connections.put(lease.soloPlayerId(), connection);
            metrics.activeConnections(connections.size());
        }));
    }
    public Mono<Boolean> disconnect(UUID soloPlayerId, String connectionId) {
        var current = connections.get(soloPlayerId);
        if (current == null || !current.connectionId().equals(connectionId)) return Mono.just(false);
        if (!connections.remove(soloPlayerId, current)) return Mono.just(false);
        metrics.activeConnections(connections.size());
        activeRequests.forEach((requestId, activeRequest) -> {
            if (activeRequest.soloPlayerId().equals(soloPlayerId)
                    && activeRequest.connectionId().equals(connectionId)) completions.fail(requestId, new ConnectionLostException());
        });
        return leases.release(soloPlayerId, connectionId);
    }
    @Override public Mono<RelayExecutionResult> execute(RelayExecutionRequest request) {
        var connection = connections.get(request.soloPlayerId());
        if (connection == null) return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION));
        Duration remaining = remaining(request);
        if (remaining.isZero() || remaining.isNegative()) return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT));
        Duration wait = remaining.compareTo(executionTimeout) < 0 ? remaining : executionTimeout;
        var pending = completions.open(request.requestId(), wait);
        if (!pending.accepted()) return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE));
        Mono<String> result = pending.result();
        activeRequests.put(request.requestId(), new ActiveRequest(request.soloPlayerId(), connection.connectionId()));
        boolean stillConnected = connections.get(request.soloPlayerId()) == connection;
        if (!stillConnected) completions.fail(request.requestId(), new ConnectionLostException());
        Mono<Void> delivery = stillConnected ? connection.transport().send(request)
                .doOnError(failure -> completions.fail(request.requestId(), failure)) : Mono.empty();
        return delivery.then(result).timeout(wait)
                .map(content -> RelayExecutionResult.success(request.requestId(), content))
                .onErrorResume(ConnectionLostException.class, ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.CONNECTION_LOST)))
                .onErrorResume(TimeoutException.class, ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT)))
                .onErrorResume(ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE)))
                .doFinally(ignored -> { activeRequests.remove(request.requestId()); completions.cancel(request.requestId()); });
    }
    public boolean complete(String requestId, String finalContent) { return completions.complete(requestId, finalContent); }
    public boolean fail(String requestId, Throwable failure) { return completions.fail(requestId, failure); }
    private static Duration remaining(RelayExecutionRequest request) {
        return request.deadlineEpochMillis() == 0 ? Duration.ofDays(1)
                : Duration.ofMillis(request.deadlineEpochMillis() - System.currentTimeMillis());
    }
    private record Connection(String connectionId, AgentConnectionTransport transport) {}
    private record ActiveRequest(UUID soloPlayerId, String connectionId) {}
}
