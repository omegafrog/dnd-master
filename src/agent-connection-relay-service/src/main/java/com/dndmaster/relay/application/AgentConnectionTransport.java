package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

/** In-memory transport supplied by a future authenticated connection endpoint. */
@FunctionalInterface
public interface AgentConnectionTransport {
    Mono<Void> send(RelayExecutionRequest request);
    default Mono<Void> sendConnectionControl(AgentConnectionControlMessage request) {
        return Mono.error(new UnsupportedOperationException("connection control is not supported by this transport"));
    }
}
