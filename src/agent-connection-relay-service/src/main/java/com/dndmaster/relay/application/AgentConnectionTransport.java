package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

/** In-memory transport supplied by a future authenticated connection endpoint. */
@FunctionalInterface
public interface AgentConnectionTransport { Mono<Void> send(RelayExecutionRequest request); }
