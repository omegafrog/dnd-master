package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface RelayConnectionControlService {
    Mono<AgentConnectionControlResult> execute(ConnectionControlRequest request);
}
