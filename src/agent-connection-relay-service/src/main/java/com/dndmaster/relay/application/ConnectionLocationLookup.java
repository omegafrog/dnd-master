package com.dndmaster.relay.application;

import java.util.Optional;
import java.util.UUID;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ConnectionLocationLookup { Mono<Optional<ConnectionLocationLease>> find(UUID soloPlayerId); }
