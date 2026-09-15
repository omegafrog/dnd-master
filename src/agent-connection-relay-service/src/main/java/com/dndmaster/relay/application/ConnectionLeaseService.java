package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/** Programmatic connection lifecycle seam; no public connection registration endpoint is exposed. */
public final class ConnectionLeaseService {
    private final ConnectionLocationRepository locations;
    private final RelayMetrics metrics;
    private final Map<UUID, String> activeConnections = new ConcurrentHashMap<>();
    public ConnectionLeaseService(ConnectionLocationRepository locations, RelayMetrics metrics) { this.locations = locations; this.metrics = metrics; }
    public Mono<Void> renew(ConnectionLocationLease lease, Duration ttl) {
        return locations.renew(lease, ttl).doOnSuccess(ignored -> {
            activeConnections.put(lease.soloPlayerId(), lease.connectionId());
            metrics.activeConnections(activeConnections.size());
        });
    }
    public Mono<Boolean> release(UUID soloPlayerId, String connectionId) {
        return locations.release(soloPlayerId, connectionId).doOnNext(released -> {
            if (released) {
                activeConnections.remove(soloPlayerId, connectionId);
                metrics.activeConnections(activeConnections.size());
            }
        });
    }
}
