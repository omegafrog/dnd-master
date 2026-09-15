package com.dndmaster.relay.application;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface LocalConnectionExecutor { Mono<RelayExecutionResult> execute(RelayExecutionRequest request); }
