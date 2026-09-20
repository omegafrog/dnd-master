package com.dndmaster.relay.infrastructure;

import com.dndmaster.relay.application.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/** Non-persistent adapter used by executable repository contract tests. */
public final class InMemoryConnectionLocationRepository implements ConnectionLocationRepository {
    private final Clock clock;
    private final Map<UUID, ConnectionLocationLease> leases = new ConcurrentHashMap<>();
    public InMemoryConnectionLocationRepository(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    @Override public Mono<Optional<ConnectionLocationLease>> find(UUID soloPlayerId) {
        return Mono.fromSupplier(() -> {
            var lease = leases.get(soloPlayerId);
            if (lease != null && !lease.expiresAt().isAfter(clock.instant())) leases.remove(soloPlayerId, lease);
            return Optional.ofNullable(leases.get(soloPlayerId));
        });
    }
    @Override public Mono<Void> claim(ConnectionLocationLease lease, Duration ttl) {
        return Mono.fromRunnable(() -> leases.put(lease.soloPlayerId(), new ConnectionLocationLease(lease.soloPlayerId(), lease.instanceId(),
                lease.internalAddress(), lease.sessionId(), lease.connectionId(), clock.instant().plus(ttl))));
    }
    @Override public Mono<Boolean> renew(ConnectionLocationLease lease, Duration ttl) {
        return Mono.fromSupplier(() -> {
            var current = leases.get(lease.soloPlayerId());
            if (current == null || !current.connectionId().equals(lease.connectionId())) return false;
            return leases.replace(lease.soloPlayerId(), current, new ConnectionLocationLease(lease.soloPlayerId(), lease.instanceId(),
                    lease.internalAddress(), lease.sessionId(), lease.connectionId(), clock.instant().plus(ttl)));
        });
    }
    @Override public Mono<Boolean> release(UUID soloPlayerId, String connectionId) {
        return Mono.fromSupplier(() -> {
            var current = leases.get(soloPlayerId);
            return current != null && current.connectionId().equals(connectionId) && leases.remove(soloPlayerId, current);
        });
    }
}
