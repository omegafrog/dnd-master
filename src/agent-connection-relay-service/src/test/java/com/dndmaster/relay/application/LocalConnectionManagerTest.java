package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.infrastructure.InMemoryConnectionLocationRepository;
import java.time.*;
import java.util.*;
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
}
