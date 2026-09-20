package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.infrastructure.InMemoryConnectionLocationRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LocalConnectionManagerTest {
    @Test void connectedTransportCompletesRequestOnceAndOldDisconnectPreservesReplacement() {
        var clock = Clock.systemUTC();
        var repository = new InMemoryConnectionLocationRepository(clock);
        var metrics = RelayMetrics.noop();
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(), metrics, Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var old = new ConnectionLocationLease(player, "a", "http://a", "s1", "old", clock.instant());
        manager.connect(old, Duration.ofSeconds(30), ignored -> Mono.empty()).block();
        var replacement = new ConnectionLocationLease(player, "a", "http://a", "s2", "new", clock.instant());
        manager.connect(replacement, Duration.ofSeconds(30), request -> {
            assertTrue(manager.complete(request.requestId(), "final"));
            assertFalse(manager.complete(request.requestId(), "duplicate"));
            return Mono.empty();
        }).block();
        assertFalse(manager.disconnect(player, "old").block());
        var request = new RelayExecutionRequest(player, "r", "w", "prompt", "model", "medium", "text", null, List.of());
        StepVerifier.create(manager.execute(request)).expectNextMatches(result -> result.success() && result.content().equals("final")).verifyComplete();
        assertEquals("new", repository.find(player).block().orElseThrow().connectionId());
    }

    @Test void sendFailureAndDisconnectCleanPendingRequestForManualRetry() {
        var clock = Clock.systemUTC();
        var repository = new InMemoryConnectionLocationRepository(clock);
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(), RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "c", clock.instant());
        manager.connect(lease, Duration.ofSeconds(30), ignored -> Mono.error(new IllegalStateException("send failed"))).block();
        var request = new RelayExecutionRequest(player, "retryable", "w", "prompt", "model", "medium", "text", null, List.of());
        StepVerifier.create(manager.execute(request)).expectNextMatches(result -> result.failureType() == RelayFailureType.REMOTE_FAILURE).verifyComplete();

        manager.connect(lease, Duration.ofSeconds(30), sent -> { manager.complete(sent.requestId(), "retried"); return Mono.empty(); }).block();
        StepVerifier.create(manager.execute(request)).expectNextMatches(result -> result.content().equals("retried")).verifyComplete();

        var waiting = new RelayExecutionRequest(player, "disconnect", "w", "prompt", "model", "medium", "text", null, List.of());
        manager.connect(lease, Duration.ofSeconds(30), ignored -> Mono.empty()).block();
        StepVerifier.create(manager.execute(waiting)).then(() -> assertTrue(manager.disconnect(player, "c").block()))
                .expectNextMatches(result -> result.failureType() == RelayFailureType.CONNECTION_LOST).verifyComplete();
    }

    @Test void duplicateRequestIsNotSentAndDeliveryIsCoveredByTheDeadline() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var sends = new AtomicInteger();
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofMillis(30));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "c", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), sent -> {
            sends.incrementAndGet();
            if (sent.requestId().equals("duplicate")) manager.complete(sent.requestId(), "done");
            return Mono.never();
        }).block();
        var request = new RelayExecutionRequest(player, "duplicate", "w", "prompt", "model", "medium", "text", null, List.of());

        var first = manager.execute(request).subscribe();
        StepVerifier.create(manager.execute(request))
                .expectNextMatches(result -> result.failureType() == RelayFailureType.REMOTE_FAILURE)
                .verifyComplete();
        assertEquals(1, sends.get());
        first.dispose();

        var timed = new RelayExecutionRequest(player, "delivery-timeout", "w", "prompt", "model", "medium", "text", null, List.of(),
                System.currentTimeMillis() + 20);
        StepVerifier.create(manager.execute(timed)).expectNextMatches(result -> result.failureType() == RelayFailureType.TIMEOUT).verifyComplete();
    }

    @Test void relayRequestPreservesPromptWhitespace() {
        var request = new RelayExecutionRequest(UUID.randomUUID(), "r", "w", "  prompt\n", "model", "medium", "text", null, List.of());
        assertEquals("  prompt\n", request.prompt());
    }

    @Test void replacingConnectionFailsRequestsOwnedByThePreviousConnection() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var old = new ConnectionLocationLease(player, "a", "http://a", "s1", "old", Instant.now());
        var replacement = new ConnectionLocationLease(player, "a", "http://a", "s2", "new", Instant.now());
        manager.connect(old, Duration.ofSeconds(30), ignored -> Mono.never()).block();
        var result = new AtomicReference<RelayExecutionResult>();
        manager.execute(new RelayExecutionRequest(player, "old-request", "w", "prompt", "model", "medium", "text", null, List.of()))
                .subscribe(result::set);

        manager.connect(replacement, Duration.ofSeconds(30), ignored -> Mono.never()).block();

        assertEquals(RelayFailureType.CONNECTION_LOST, result.get().failureType());
    }

    @Test void disconnectDuringDeliveryReturnsConnectionLostBeforeTimeout() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(5));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "c", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), ignored -> Mono.never()).block();
        var result = new AtomicReference<RelayExecutionResult>();
        manager.execute(new RelayExecutionRequest(player, "disconnect-during-delivery", "w", "prompt", "model", "medium", "text", null, List.of()))
                .subscribe(result::set);

        assertTrue(manager.disconnect(player, "c").block());
        assertEquals(RelayFailureType.CONNECTION_LOST, result.get().failureType());
    }

    @Test void rejectsFinalResponseOverUtf8PayloadLimit() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(1), 4);
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "c", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), request -> {
            manager.complete(request.requestId(), "가가");
            return Mono.empty();
        }).block();
        var request = new RelayExecutionRequest(player, "oversized", "w", "prompt", "model", "medium", "text", null, List.of());

        StepVerifier.create(manager.execute(request)).expectNextMatches(result -> result.failureType() == RelayFailureType.REMOTE_FAILURE).verifyComplete();
    }

    @Test void disconnectAfterResultBeforeDeliveryStillReturnsConnectionLost() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(5));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "c", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), request -> {
            manager.complete(request.requestId(), "early");
            return Mono.never();
        }).block();
        var result = new AtomicReference<RelayExecutionResult>();
        manager.execute(new RelayExecutionRequest(player, "result-before-delivery", "w", "prompt", "model", "medium", "text", null, List.of()))
                .subscribe(result::set);

        assertTrue(manager.disconnect(player, "c").block());
        assertEquals(RelayFailureType.CONNECTION_LOST, result.get().failureType());
    }

    @Test void rejectsRequestForAConnectionThatIsNoLongerActive() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "current", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), ignored -> { fail("stale connection must not send"); return Mono.empty(); }).block();
        var request = new RelayExecutionRequest(player, "stale", "w", "prompt", "model", "medium", "text", null, List.of())
                .withConnectionId("replaced");

        StepVerifier.create(manager.execute(request)).expectNextMatches(result -> result.failureType() == RelayFailureType.CONNECTION_LOST).verifyComplete();
    }

    @Test void rejectsLocalConnectionWhenRedisNowPointsToAnotherInstance() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var local = new ConnectionLocationLease(player, "a", "http://a", "s1", "local", Instant.now());
        manager.connect(local, Duration.ofSeconds(30), ignored -> {
            fail("stale local connection must not send");
            return Mono.empty();
        }).block();
        var remote = new ConnectionLocationLease(player, "b", "http://b", "s2", "remote", Instant.now());
        repository.claim(remote, Duration.ofSeconds(30)).block();

        StepVerifier.create(manager.execute(new RelayExecutionRequest(player, "cross-instance-stale", "w", "prompt", "model", "medium", "text", null, List.of())))
                .expectNextMatches(result -> result.failureType() == RelayFailureType.CONNECTION_LOST)
                .verifyComplete();
    }

    @Test void cancelledConnectionClaimIsNotActivatedAfterLeaseRegistration() {
        reactor.core.publisher.Sinks.Empty<Void> claim = reactor.core.publisher.Sinks.empty();
        var releases = new AtomicInteger();
        var repository = new ConnectionLocationRepository() {
            @Override public Mono<Optional<ConnectionLocationLease>> find(UUID soloPlayerId) { return Mono.just(Optional.empty()); }
            @Override public Mono<Void> claim(ConnectionLocationLease lease, Duration ttl) { return claim.asMono(); }
            @Override public Mono<Boolean> renew(ConnectionLocationLease lease, Duration ttl) { return Mono.just(false); }
            @Override public Mono<Boolean> release(UUID soloPlayerId, String connectionId) { releases.incrementAndGet(); return Mono.just(true); }
        };
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository), new RequestCompletionRegistry(),
                RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "a", "http://a", "s", "pending", Instant.now());
        var connection = manager.connect(lease, Duration.ofSeconds(30), ignored -> Mono.empty()).subscribe();

        assertTrue(manager.disconnect(player, "pending").block());
        claim.tryEmitEmpty();
        connection.dispose();
        assertEquals(1, releases.get());
        StepVerifier.create(manager.execute(new RelayExecutionRequest(player, "not-active", "w", "prompt", "model", "medium", "text", null, List.of())))
                .expectNextMatches(result -> result.failureType() == RelayFailureType.NO_CONNECTION).verifyComplete();
    }
}
