package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Programmatic connection lifecycle seam; no public connection registration endpoint is exposed. */
public final class ConnectionLeaseService {
    private final ConnectionLocationRepository locations;
    public ConnectionLeaseService(ConnectionLocationRepository locations) { this.locations = locations; }
    public Mono<Void> renew(ConnectionLocationLease lease, Duration ttl) {
        return locations.renew(lease, ttl);
    }
    public Mono<Boolean> release(UUID soloPlayerId, String connectionId) {
        return locations.release(soloPlayerId, connectionId);
    }
}
