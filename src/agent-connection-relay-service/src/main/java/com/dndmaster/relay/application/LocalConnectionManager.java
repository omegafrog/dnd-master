package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import reactor.core.publisher.Mono;

public final class LocalConnectionManager implements LocalConnectionExecutor {
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
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
        connections.put(lease.soloPlayerId(), connection);
        metrics.activeConnections(connections.size());
        return leases.renew(lease, ttl).doOnError(ignored -> {
            connections.remove(lease.soloPlayerId(), connection);
            metrics.activeConnections(connections.size());
        });
    }
    public Mono<Boolean> disconnect(UUID soloPlayerId, String connectionId) {
        var current = connections.get(soloPlayerId);
        if (current == null || !current.connectionId().equals(connectionId)) return Mono.just(false);
        connections.remove(soloPlayerId, current);
        metrics.activeConnections(connections.size());
        return leases.release(soloPlayerId, connectionId);
    }
    @Override public Mono<RelayExecutionResult> execute(RelayExecutionRequest request) {
        var connection = connections.get(request.soloPlayerId());
        if (connection == null) return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION));
        Mono<String> result = completions.await(request.requestId(), executionTimeout);
        return connection.transport().send(request).then(result)
                .map(content -> RelayExecutionResult.success(request.requestId(), content))
                .onErrorResume(TimeoutException.class, ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT)))
                .onErrorResume(ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE)));
    }
    public boolean complete(String requestId, String finalContent) { return completions.complete(requestId, finalContent); }
    public boolean fail(String requestId, Throwable failure) { return completions.fail(requestId, failure); }
    private record Connection(String connectionId, AgentConnectionTransport transport) {}
}
