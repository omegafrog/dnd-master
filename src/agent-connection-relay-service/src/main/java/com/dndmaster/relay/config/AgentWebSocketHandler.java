package com.dndmaster.relay.config;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.dndmaster.relay.application.ConnectionLeaseHeartbeat;
import com.dndmaster.relay.application.ConnectionLeaseService;
import com.dndmaster.relay.application.ConnectionLocationLease;
import com.dndmaster.relay.application.IdentityServicePort;
import com.dndmaster.relay.application.LocalConnectionRegistry;
import com.dndmaster.relay.application.RelayExecutionResult;
import com.dndmaster.relay.application.RequestCompletionRegistry;
import com.dndmaster.relay.infrastructure.WebSocketConnectionTransport;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class AgentWebSocketHandler implements WebSocketHandler {
  private static final Duration LEASE_TTL = Duration.ofSeconds(60);
  private static final Duration LEASE_RENEW_INTERVAL = Duration.ofSeconds(20);

  private final LocalConnectionRegistry registry;
  private final RelayInstanceProperties properties;
  private final IdentityServicePort identityPort;
  private final ObjectMapper objectMapper;
  private final RequestCompletionRegistry completionRegistry;
  private final ConnectionLeaseHeartbeat leaseHeartbeat;

  public AgentWebSocketHandler(IdentityServicePort ientityPort, LocalConnectionRegistry registry,
      RelayInstanceProperties properties, ObjectMapper objectMapper, RequestCompletionRegistry completionRegistry,
      ConnectionLeaseService leaseService) {
    this.identityPort = ientityPort;
    this.registry = registry;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.completionRegistry = completionRegistry;
    this.leaseHeartbeat = new ConnectionLeaseHeartbeat(
        leaseService, LEASE_TTL, LEASE_RENEW_INTERVAL);
  }

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    String authentication = session.getHandshakeInfo().getHeaders()
        .getFirst(HttpHeaders.AUTHORIZATION);

    UUID soloPlayerId = identityPort.introspectUser(bearerToken(authentication));
    String connectionId = UUID.randomUUID().toString();
    ConnectionLocationLease lease = new ConnectionLocationLease(
        soloPlayerId,
        properties.instanceId(),
        properties.internalAddress(),
        session.getId(),
        connectionId,
        Instant.now().plus(LEASE_TTL));
    WebSocketConnectionTransport transport = new WebSocketConnectionTransport(session, objectMapper);
    Mono<Void> receive = session.receive()
        .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
        .flatMap(message -> Mono.fromCallable(() -> objectMapper.readValue(
            message.getPayloadAsText(), RelayExecutionResult.class))
            .doOnNext(result -> {
              if (result.success()) {
                completionRegistry.complete(result.requestId(), result.content());
              } else {
                completionRegistry.fail(result.requestId(),
                    new IllegalStateException(result.failureType().name()));
              }
            }))
        .then();
    Mono<Void> sessionLifecycle = Mono.when(transport.startSend(), receive);

    return Mono.usingWhen(
        registry.connect(lease, LEASE_TTL, transport).thenReturn(lease),
        ignored -> Mono.firstWithSignal(sessionLifecycle, leaseHeartbeat.run(lease)),
        ignored -> registry.disconnect(soloPlayerId, connectionId).then(),
        (ignored, error) -> registry.disconnect(soloPlayerId, connectionId).then(),
        ignored -> registry.disconnect(soloPlayerId, connectionId).then());
  }

  private static String bearerToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Bearer authorization is required");
    }
    String token = authorization.substring("Bearer ".length()).trim();
    if (token.isEmpty()) {
      throw new IllegalArgumentException("Bearer token is required");
    }
    return token;
  }

}
