package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface OwnedInstanceClient { Mono<RelayExecutionResult> execute(String internalAddress, RelayExecutionRequest request); }
