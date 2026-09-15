package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ConnectionLocationRepository {
    Mono<Optional<ConnectionLocationLease>> find(UUID soloPlayerId);
    Mono<Void> claim(ConnectionLocationLease lease, Duration ttl);
    Mono<Boolean> renew(ConnectionLocationLease lease, Duration ttl);
    Mono<Boolean> release(UUID soloPlayerId, String connectionId);
}
