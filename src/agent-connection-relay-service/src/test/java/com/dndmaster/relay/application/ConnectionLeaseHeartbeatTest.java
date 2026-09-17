package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ConnectionLeaseHeartbeatTest {

    @Test
    void renewsLeaseAtConfiguredInterval() {
        var renewals = new AtomicInteger();
        var repository = new StubConnectionLocationRepository(renewals);
        var heartbeat = new ConnectionLeaseHeartbeat(
                new ConnectionLeaseService(repository),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10));

        StepVerifier.withVirtualTime(() -> heartbeat.run(lease()))
                .thenAwait(Duration.ofSeconds(9))
                .then(() -> assertEquals(0, renewals.get()))
                .thenAwait(Duration.ofSeconds(1))
                .then(() -> assertEquals(1, renewals.get()))
                .thenCancel()
                .verify();
    }

    private static ConnectionLocationLease lease() {
        return new ConnectionLocationLease(
                UUID.randomUUID(),
                "relay-a",
                "http://relay-a:8080",
                "session-1",
                "connection-1",
                Instant.now().plusSeconds(30));
    }

    private static final class StubConnectionLocationRepository implements ConnectionLocationRepository {
        private final AtomicInteger renewals;

        private StubConnectionLocationRepository(AtomicInteger renewals) {
            this.renewals = renewals;
        }

        @Override
        public Mono<Optional<ConnectionLocationLease>> find(UUID soloPlayerId) {
            return Mono.just(Optional.empty());
        }

        @Override
        public Mono<Void> claim(ConnectionLocationLease lease, Duration ttl) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> renew(ConnectionLocationLease lease, Duration ttl) {
            return Mono.fromSupplier(() -> {
                renewals.incrementAndGet();
                return true;
            });
        }

        @Override
        public Mono<Boolean> release(UUID soloPlayerId, String connectionId) {
            return Mono.just(true);
        }
    }
}
