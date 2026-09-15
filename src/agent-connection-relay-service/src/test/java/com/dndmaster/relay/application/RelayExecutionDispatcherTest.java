package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RelayExecutionDispatcherTest {
    private static final RelayExecutionRequest REQUEST = new RelayExecutionRequest(
            UUID.randomUUID(), "request-1", "work-1", "sensitive prompt", "model", "medium", "text", null, List.of());

    @Test void dispatchesLocallyWithoutInternalHttpWhenThisInstanceOwnsConnection() {
        var lease = new ConnectionLocationLease(REQUEST.soloPlayerId(), "a", "http://a", "session", "connection", Instant.now().plusSeconds(30));
        var remoteCalls = new AtomicInteger();
        var dispatcher = new RelayExecutionDispatcher("a", id -> Mono.just(Optional.of(lease)),
                request -> Mono.just(RelayExecutionResult.success(request.requestId(), "done")),
                (address, request) -> { remoteCalls.incrementAndGet(); return Mono.error(new AssertionError()); }, RelayMetrics.noop(), Duration.ofSeconds(2));

        StepVerifier.create(dispatcher.execute(REQUEST)).expectNextMatches(result -> result.success() && result.content().equals("done")).verifyComplete();
        assertEquals(0, remoteCalls.get());
    }

    @Test void dispatchesDirectlyToOwningInstance() {
        var lease = new ConnectionLocationLease(REQUEST.soloPlayerId(), "c", "http://relay-c:8080", "session", "connection", Instant.now().plusSeconds(30));
        var dispatcher = new RelayExecutionDispatcher("a", id -> Mono.just(Optional.of(lease)),
                request -> Mono.error(new AssertionError()),
                (address, request) -> Mono.just(RelayExecutionResult.success(request.requestId(), address)), RelayMetrics.noop(), Duration.ofSeconds(2));

        StepVerifier.create(dispatcher.execute(REQUEST)).expectNextMatches(result -> result.content().equals("http://relay-c:8080")).verifyComplete();
    }

    @Test void returnsTypedFailuresWithoutRetry() {
        var lookups = new AtomicInteger();
        var absent = new RelayExecutionDispatcher("a", id -> { lookups.incrementAndGet(); return Mono.just(Optional.empty()); },
                request -> Mono.never(), (address, request) -> Mono.never(), RelayMetrics.noop(), Duration.ofMillis(20));
        StepVerifier.create(absent.execute(REQUEST)).expectNextMatches(r -> r.failureType() == RelayFailureType.NO_CONNECTION).verifyComplete();
        assertEquals(1, lookups.get());

        var lease = new ConnectionLocationLease(REQUEST.soloPlayerId(), "a", "http://a", "s", "c", Instant.now().plusSeconds(1));
        var timeout = new RelayExecutionDispatcher("a", id -> Mono.just(Optional.of(lease)), request -> Mono.never(),
                (address, request) -> Mono.never(), RelayMetrics.noop(), Duration.ofMillis(10));
        StepVerifier.create(timeout.execute(REQUEST)).expectNextMatches(r -> r.failureType() == RelayFailureType.TIMEOUT).verifyComplete();

        var failed = new RelayExecutionDispatcher("a", id -> Mono.error(new IllegalStateException("redis unavailable")), request -> Mono.never(),
                (address, request) -> Mono.never(), RelayMetrics.noop(), Duration.ofSeconds(1));
        StepVerifier.create(failed.execute(REQUEST)).expectNextMatches(r -> r.failureType() == RelayFailureType.REMOTE_FAILURE).verifyComplete();
    }
}
