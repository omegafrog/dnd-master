package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.infrastructure.InMemoryConnectionLocationRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
        manager.connect(lease, Duration.ofSeconds(30), ignored -> { sends.incrementAndGet(); return Mono.never(); }).block();
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
}
