package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.UUID;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;

public interface LocalConnectionRegistry {
  Mono<Void> connect(ConnectionLocationLease lease, Duration ttl, AgentConnectionTransport transport);

  Mono<Boolean> disconnect(UUID soloPlayerId, String connectionId);

  boolean complete(String requestId, String finalContent);

  boolean fail(String requestId, Throwable failure);

  default JsonNode activeRagSearchContext(UUID soloPlayerId, String connectionId, String requestId) { return null; }
}
