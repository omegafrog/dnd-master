package com.dndmaster.relay.config;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.dndmaster.relay.infrastructure.HttpRagToolSearch;
import com.dndmaster.relay.infrastructure.HttpScenarioElementLookup;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class AgentWebSocketHandler implements WebSocketHandler {
  private static final String CONNECTION_ID_HEADER = "X-Agent-Connection-Id";
  private static final Duration LEASE_TTL = Duration.ofSeconds(60);
  private static final Duration LEASE_RENEW_INTERVAL = Duration.ofSeconds(20);

  private final LocalConnectionRegistry registry;
  private final RelayInstanceProperties properties;
  private final IdentityServicePort identityPort;
  private final ObjectMapper objectMapper;
  private final RequestCompletionRegistry completionRegistry;
  private final ConnectionLeaseHeartbeat leaseHeartbeat;
  private final HttpRagToolSearch ragToolSearch;
  private final HttpScenarioElementLookup scenarioElementLookup;
  private final java.util.Set<String> inFlightToolCalls = java.util.concurrent.ConcurrentHashMap.newKeySet();

  @Autowired
  public AgentWebSocketHandler(IdentityServicePort ientityPort, LocalConnectionRegistry registry,
      RelayInstanceProperties properties, ObjectMapper objectMapper, RequestCompletionRegistry completionRegistry,
      ConnectionLeaseService leaseService, HttpRagToolSearch ragToolSearch,
      HttpScenarioElementLookup scenarioElementLookup) {
    this.identityPort = ientityPort;
    this.registry = registry;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.completionRegistry = completionRegistry;
    this.leaseHeartbeat = new ConnectionLeaseHeartbeat(
        leaseService, LEASE_TTL, LEASE_RENEW_INTERVAL);
    this.ragToolSearch = ragToolSearch;
    this.scenarioElementLookup = scenarioElementLookup;
  }

  public AgentWebSocketHandler(IdentityServicePort identityPort, LocalConnectionRegistry registry,
      RelayInstanceProperties properties, ObjectMapper objectMapper, RequestCompletionRegistry completionRegistry,
      ConnectionLeaseService leaseService, HttpRagToolSearch ragToolSearch) {
    this(identityPort, registry, properties, objectMapper, completionRegistry, leaseService, ragToolSearch, null);
  }

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    String authentication = session.getHandshakeInfo().getHeaders()
        .getFirst(HttpHeaders.AUTHORIZATION);

    UUID soloPlayerId = identityPort.introspectUser(bearerToken(authentication));
    String connectionId = connectionId(session);
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
        .flatMap(message -> receiveMessage(message.getPayloadAsText(), soloPlayerId, connectionId, transport))
        .then();
    Mono<Void> sessionLifecycle = Mono.when(transport.startSend(), receive);

    return Mono.usingWhen(
        registry.connect(lease, LEASE_TTL, transport).thenReturn(lease),
        ignored -> Mono.firstWithSignal(sessionLifecycle, leaseHeartbeat.run(lease)),
        ignored -> registry.disconnect(soloPlayerId, connectionId).then(),
        (ignored, error) -> registry.disconnect(soloPlayerId, connectionId).then(),
        ignored -> registry.disconnect(soloPlayerId, connectionId).then());
  }

  Mono<Void> receiveMessage(String text, UUID soloPlayerId, String connectionId,
      WebSocketConnectionTransport transport) {
    return Mono.fromCallable(() -> objectMapper.readTree(text)).flatMap(node -> {
      if ("CONNECTION_CONTROL_RESULT".equals(node.path("messageType").asText())) {
        try {
          var control = objectMapper.treeToValue(node,
              com.dndmaster.relay.application.AgentConnectionControlResult.class);
          completionRegistry.complete(RelayExecutionResult.success(control.requestId(),
              objectMapper.writeValueAsString(control)));
        } catch (Exception failure) {
          completionRegistry.fail(node.path("requestId").asText(),
              new IllegalStateException("connection result could not be decoded", failure));
        }
        return Mono.empty();
      }
      if ("mcp_tool_call".equals(node.path("type").asText())) {
        String requestId = node.path("requestId").asText("");
        String callId = node.path("callId").asText("");
        String tool = node.path("tool").asText("");
        String query = node.path("arguments").path("query").asText("");
        if (!("search_rules".equals(tool) || "find_scenario_elements".equals(tool)) || requestId.isBlank() || callId.isBlank()) {
          logMcpRejected(requestId, tool, query, "INVALID_TOOL_CALL");
          return sendToolResult(transport, requestId, callId, false, null, "invalid MCP tool call");
        }
        var scope = registry.activeRagSearchContext(soloPlayerId, connectionId, requestId);
        if (scope == null) {
          logMcpRejected(requestId, tool, query, "NO_ACTIVE_SCOPE");
          return sendToolResult(transport, requestId, callId, false, null, "no active authorized search scope");
        }
        if ("find_scenario_elements".equals(tool) && scenarioElementLookup == null) {
          logMcpRejected(requestId, tool, query, "LOOKUP_UNAVAILABLE");
          return sendToolResult(transport, requestId, callId, false, null, "scenario lookup is unavailable");
        }
        String callKey = connectionId + ":" + callId;
        if (!inFlightToolCalls.add(callKey)) {
          logMcpRejected(requestId, tool, query, "DUPLICATE_CALL");
          return sendToolResult(transport, requestId, callId, false, null, "duplicate tool call");
        }
        long startedAt = System.nanoTime();
        if (diagnosticsEnabled()) org.slf4j.LoggerFactory.getLogger(AgentWebSocketHandler.class).info(
            "dev_mcp_tool stage=start requestId={} tool={} queryChars={} sessionId={} packageId={}", requestId, tool,
            query.length(), scope.path("sessionId").asText(""), scope.path("scenarioPackageId").asText(""));
        return Mono.fromCallable(() -> "search_rules".equals(tool)
                ? ragToolSearch.search(soloPlayerId, scope, query)
                : scenarioElementLookup.search(soloPlayerId, scope, query))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .flatMap(result -> {
              if (diagnosticsEnabled()) org.slf4j.LoggerFactory.getLogger(AgentWebSocketHandler.class).info(
                  "dev_mcp_tool stage=complete requestId={} tool={} resultCount={} elapsedMs={}", requestId, tool,
                  result.path("elements").isArray() ? result.path("elements").size()
                      : result.path("evidence").isArray() ? result.path("evidence").size() : 0,
                  (System.nanoTime() - startedAt) / 1_000_000);
              return sendToolResult(transport, requestId, callId, true, result, "");
            })
            .onErrorResume(failure -> {
              if (diagnosticsEnabled()) org.slf4j.LoggerFactory.getLogger(AgentWebSocketHandler.class).warn(
                  "dev_mcp_tool stage=failed requestId={} tool={} queryChars={} failureClass={} elapsedMs={} recovery=tool_error_returned",
                  requestId, tool, query.length(), failure.getClass().getSimpleName(), (System.nanoTime() - startedAt) / 1_000_000);
              return sendToolResult(transport, requestId, callId, false, null,
                  failure.getMessage() == null ? "MCP lookup failed" : failure.getMessage());
            })
            .doFinally(ignored -> inFlightToolCalls.remove(callKey));
      }
      try {
        RelayExecutionResult result = objectMapper.treeToValue(node, RelayExecutionResult.class);
        completionRegistry.complete(result);
        return Mono.empty();
      } catch (Exception failure) {
        String requestId = node.path("requestId").asText("");
        if (!requestId.isBlank()) {
          completionRegistry.fail(requestId, new IllegalStateException("agent execution result could not be decoded", failure));
          return Mono.empty();
        }
        return Mono.error(failure);
      }
    });
  }

  private static boolean diagnosticsEnabled() {
    return "true".equalsIgnoreCase(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
        System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
  }

  private static void logMcpRejected(String requestId, String tool, String query, String reason) {
    if (diagnosticsEnabled()) org.slf4j.LoggerFactory.getLogger(AgentWebSocketHandler.class).warn(
        "dev_mcp_tool stage=rejected requestId={} tool={} queryChars={} reason={} recovery=tool_error_returned",
        requestId, tool, query.length(), reason);
  }

  private Mono<Void> sendToolResult(WebSocketConnectionTransport transport, String requestId, String callId,
      boolean success, com.fasterxml.jackson.databind.JsonNode result, String error) {
    var response = objectMapper.createObjectNode().put("type", "mcp_tool_result").put("requestId", requestId)
        .put("callId", callId).put("success", success);
    if (result != null) response.set("result", result);
    if (!success) response.put("error", error);
    return transport.sendText(response.toString());
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

  private static String connectionId(WebSocketSession session) {
    String requested = session.getHandshakeInfo().getHeaders().getFirst(CONNECTION_ID_HEADER);
    if (requested == null || requested.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return UUID.fromString(requested.trim()).toString();
  }

}
