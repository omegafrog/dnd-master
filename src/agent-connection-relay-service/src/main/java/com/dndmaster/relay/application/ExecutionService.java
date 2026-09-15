package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ExecutionService { Mono<RelayExecutionResult> execute(RelayExecutionRequest request); }
