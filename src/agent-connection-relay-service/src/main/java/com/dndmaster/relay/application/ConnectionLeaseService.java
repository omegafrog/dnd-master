package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Programmatic connection lifecycle seam; no public connection registration endpoint is exposed. */
public final class ConnectionLeaseService {
    private final ConnectionLocationRepository locations;
    public ConnectionLeaseService(ConnectionLocationRepository locations) { this.locations = locations; }
    public Mono<Void> claim(ConnectionLocationLease lease, Duration ttl) {
        return locations.claim(lease, ttl);
    }
    public Mono<Boolean> renew(ConnectionLocationLease lease, Duration ttl) { return locations.renew(lease, ttl); }
    public Mono<Boolean> release(UUID soloPlayerId, String connectionId) {
        return locations.release(soloPlayerId, connectionId);
    }
    public Mono<Boolean> isCurrent(ConnectionLocationLease expected) {
        return locations.find(expected.soloPlayerId()).map(found -> found
                .filter(actual -> actual.instanceId().equals(expected.instanceId())
                        && actual.connectionId().equals(expected.connectionId()))
                .isPresent());
    }
}
