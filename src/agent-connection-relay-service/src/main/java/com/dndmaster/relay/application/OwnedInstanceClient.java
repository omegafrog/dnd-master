package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface OwnedInstanceClient {
    Mono<RelayExecutionResult> execute(String internalAddress, RelayExecutionRequest request);
    default Mono<AgentConnectionControlResult> control(String internalAddress, ConnectionControlRequest request) {
        return Mono.just(AgentConnectionControlResult.failure(request.requestId(), "UNAVAILABLE", "사용자 PC 연결을 사용할 수 없습니다."));
    }
}
