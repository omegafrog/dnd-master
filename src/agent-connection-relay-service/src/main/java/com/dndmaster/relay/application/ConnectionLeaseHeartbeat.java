package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.Objects;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Renews a live connection location until the owning WebSocket session ends. */
public final class ConnectionLeaseHeartbeat {
    private final ConnectionLeaseService leases;
    private final Duration ttl;
    private final Duration interval;

    public ConnectionLeaseHeartbeat(ConnectionLeaseService leases, Duration ttl, Duration interval) {
        this.leases = Objects.requireNonNull(leases);
        this.ttl = positive(ttl, "ttl");
        this.interval = positive(interval, "interval");
        if (!interval.minus(ttl).isNegative()) {
            throw new IllegalArgumentException("interval must be shorter than ttl");
        }
    }

    public Mono<Void> run(ConnectionLocationLease lease) {
        return Flux.interval(interval)
                .concatMap(ignored -> leases.renew(lease, ttl)
                        .flatMap(renewed -> renewed
                                ? Mono.<Void>empty()
                                : Mono.error(new IllegalStateException("connection lease is no longer current"))))
                .then();
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
