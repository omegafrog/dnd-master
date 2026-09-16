package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface LocalConnectionRegistry {
    Mono<Void> connect(ConnectionLocationLease lease, Duration ttl, AgentConnectionTransport transport);

    Mono<Boolean> disconnect(UUID soloPlayerId, String connectionId);

    boolean complete(String requestId, String finalContent);

    boolean fail(String requestId, Throwable failure);
}
