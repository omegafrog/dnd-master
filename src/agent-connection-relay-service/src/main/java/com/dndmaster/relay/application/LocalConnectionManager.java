package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class LocalConnectionManager implements LocalConnectionExecutor, LocalConnectionRegistry {
  private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
  private final Map<UUID, Connection> connecting = new ConcurrentHashMap<>();
  private final Map<UUID, Object> lifecycleLocks = new ConcurrentHashMap<>();
  private final Map<String, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
  private final ConnectionLeaseService leases;
  private final RequestCompletionRegistry completions;
  private final RelayMetrics metrics;
  private final Duration executionTimeout;
  private final int maxPayloadBytes;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public LocalConnectionManager(ConnectionLeaseService leases, RequestCompletionRegistry completions,
      RelayMetrics metrics, Duration executionTimeout) {
    this(leases, completions, metrics, executionTimeout, 16_777_216);
  }

  public LocalConnectionManager(ConnectionLeaseService leases, RequestCompletionRegistry completions,
      RelayMetrics metrics, Duration executionTimeout, int maxPayloadBytes) {
    if (maxPayloadBytes <= 0)
      throw new IllegalArgumentException("maxPayloadBytes must be positive");
    this.leases = leases;
    this.completions = completions;
    this.metrics = metrics;
    this.executionTimeout = executionTimeout;
    this.maxPayloadBytes = maxPayloadBytes;
  }

  public Mono<Void> connect(ConnectionLocationLease lease, Duration ttl, AgentConnectionTransport transport) {
    var connection = new Connection(lease.connectionId(), lease, transport);
    var lifecycleLock = lifecycleLock(lease.soloPlayerId());
    synchronized (lifecycleLock) {
      connecting.put(lease.soloPlayerId(), connection);
    }
    return leases.claim(lease, ttl).then(Mono.defer(() -> {
      synchronized (lifecycleLock) {
        if (!connecting.remove(lease.soloPlayerId(), connection))
          return leases.release(lease.soloPlayerId(), lease.connectionId()).then();
        var previous = connections.put(lease.soloPlayerId(), connection);
        metrics.activeConnections(connections.size());
        if (previous != null && !previous.connectionId().equals(connection.connectionId())) {
          activeRequests.forEach((requestId, activeRequest) -> {
            if (activeRequest.soloPlayerId().equals(lease.soloPlayerId())
                && activeRequest.connectionId().equals(previous.connectionId())) {
              activeRequest.connectionLost().tryEmitError(new ConnectionLostException());
              completions.fail(requestId, new ConnectionLostException());
            }
          });
        }
      }
      return Mono.empty();
    })).doOnError(ignored -> {
      synchronized (lifecycleLock) {
        connecting.remove(lease.soloPlayerId(), connection);
      }
    });
  }

  public Mono<Boolean> disconnect(UUID soloPlayerId, String connectionId) {
    var lifecycleLock = lifecycleLock(soloPlayerId);
    synchronized (lifecycleLock) {
      var current = connections.get(soloPlayerId);
      if (current == null || !current.connectionId().equals(connectionId)) {
        var pending = connecting.get(soloPlayerId);
        return pending != null && pending.connectionId().equals(connectionId)
            && connecting.remove(soloPlayerId, pending)
                ? Mono.just(true)
                : Mono.just(false);
      }
      if (!connections.remove(soloPlayerId, current))
        return Mono.just(false);
      metrics.activeConnections(connections.size());
      activeRequests.forEach((requestId, activeRequest) -> {
        if (activeRequest.soloPlayerId().equals(soloPlayerId)
            && activeRequest.connectionId().equals(connectionId)) {
          activeRequest.connectionLost().tryEmitError(new ConnectionLostException());
          completions.fail(requestId, new ConnectionLostException());
        }
      });
      return leases.release(soloPlayerId, connectionId);
    }
  }

  @Override
  public Mono<RelayExecutionResult> execute(RelayExecutionRequest request) {
    var connection = connections.get(request.soloPlayerId());
    if (connection == null)
      return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION));
    if (!request.connectionId().isBlank() && !request.connectionId().equals(connection.connectionId()))
      return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.CONNECTION_LOST));

    Duration remaining = remaining(request);
    if (remaining.isZero() || remaining.isNegative())
      return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT));

    Duration wait = remaining.compareTo(executionTimeout) < 0 ? remaining : executionTimeout;

    return leases.isCurrent(connection.lease()).timeout(wait).flatMap(current -> {
      if (!current)
        return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.CONNECTION_LOST));
      var pending = completions.open(request.requestId(), wait);
      if (!pending.accepted())
        return Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE));
      var connectionLost = Sinks.<Throwable>one();
      var activeRequest = new ActiveRequest(request.soloPlayerId(), connection.connectionId(), connectionLost);
      var lifecycleLock = lifecycleLock(request.soloPlayerId());
      synchronized (lifecycleLock) {
        if (connections.get(request.soloPlayerId()) != connection) {
          connectionLost.tryEmitError(new ConnectionLostException());
          completions.fail(request.requestId(), new ConnectionLostException());
        } else
          activeRequests.put(request.requestId(), activeRequest);
      }
      Mono<RelayExecutionResult> result = pending.result();
      Mono<Void> delivery = connections.get(request.soloPlayerId()) == connection
          ? Mono.defer(() -> connection.transport().send(request))
              .doOnError(failure -> completions.fail(request.requestId(), failure))
          : Mono.empty();
      Mono<RelayExecutionResult> operation = Mono.zip(delivery.thenReturn(true), result, (ignored, response) -> response).timeout(wait);
      Mono<RelayExecutionResult> disconnected = connectionLost.asMono().flatMap(failure -> Mono.error(failure));
      return Mono.firstWithSignal(operation, disconnected)
          .map(response -> {
            if (jsonBytes(response) > maxPayloadBytes)
              throw new PayloadTooLargeException();
            return response;
          })
          .onErrorResume(PayloadTooLargeException.class,
              ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE)))
          .onErrorResume(ConnectionLostException.class,
              ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.CONNECTION_LOST)))
          .onErrorResume(TimeoutException.class,
              ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT)))
          .onErrorResume(
              ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE)))
          .doFinally(ignored -> {
            activeRequests.remove(request.requestId());
            completions.cancel(request.requestId());
            completions.close(request.requestId());
          });
    }).onErrorResume(ConnectionLostException.class,
        ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.CONNECTION_LOST)))
        .onErrorResume(TimeoutException.class,
            ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.TIMEOUT)))
        .onErrorResume(
            ignored -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.REMOTE_FAILURE)));
  }

  @Override
  public Mono<AgentConnectionControlResult> control(ConnectionControlRequest request) {
    var connection = connections.get(request.soloPlayerId());
    if (connection == null)
      return Mono.just(AgentConnectionControlResult.failure(request.requestId(), "UNAVAILABLE", "사용자 PC 연결을 사용할 수 없습니다."));
    if (!request.connectionId().isBlank() && !request.connectionId().equals(connection.connectionId()))
      return Mono.just(AgentConnectionControlResult.failure(request.requestId(), "UNAVAILABLE", "현재 설치 연결이 변경되었습니다."));
    Duration remaining = request.deadlineEpochMillis() == 0 ? executionTimeout
        : Duration.ofMillis(request.deadlineEpochMillis() - System.currentTimeMillis());
    if (remaining.isZero() || remaining.isNegative())
      return Mono.just(AgentConnectionControlResult.failure(request.requestId(), "TIMEOUT", "연결 상태 확인 시간이 초과되었습니다."));
    Duration wait = remaining.compareTo(executionTimeout) < 0 ? remaining : executionTimeout;
    return leases.isCurrent(connection.lease()).timeout(wait).flatMap(current -> {
      if (!current) return Mono.just(AgentConnectionControlResult.failure(request.requestId(), "UNAVAILABLE", "사용자 PC 연결을 사용할 수 없습니다."));
      var pending = completions.open(request.requestId(), wait);
      if (!pending.accepted()) return Mono.just(AgentConnectionControlResult.failure(request.requestId(), "FAILED", "중복 연결 요청입니다."));
      var connectionLost = Sinks.<Throwable>one();
      var activeRequest = new ActiveRequest(request.soloPlayerId(), connection.connectionId(), connectionLost);
      var lifecycleLock = lifecycleLock(request.soloPlayerId());
      synchronized (lifecycleLock) {
        if (connections.get(request.soloPlayerId()) != connection) {
          connectionLost.tryEmitError(new ConnectionLostException());
          completions.fail(request.requestId(), new ConnectionLostException());
        } else activeRequests.put(request.requestId(), activeRequest);
      }
      var message = new AgentConnectionControlMessage(request.requestId(), request.action(),
          request.operationId(), request.operationType());
      Mono<Void> delivery = connections.get(request.soloPlayerId()) == connection
          ? Mono.defer(() -> connection.transport().sendConnectionControl(message))
              .doOnError(failure -> completions.fail(request.requestId(), failure))
          : Mono.empty();
      Mono<RelayExecutionResult> operation = Mono.zip(delivery.thenReturn(true), pending.result(),
          (ignored, response) -> response).timeout(wait);
      Mono<RelayExecutionResult> disconnected = connectionLost.asMono().flatMap(failure -> Mono.error(failure));
      return Mono.firstWithSignal(operation, disconnected)
          .map(response -> {
            if (jsonBytes(response) > maxPayloadBytes) throw new PayloadTooLargeException();
            return response;
          })
          .map(this::decodeConnectionControlResult)
          .map(result -> {
            if (!request.requestId().equals(result.requestId()))
              throw new IllegalStateException("connection result correlation did not match");
            return result;
          })
          .onErrorResume(PayloadTooLargeException.class, ignored -> Mono.just(
              AgentConnectionControlResult.failure(request.requestId(), "FAILED", "연결 응답 크기가 제한을 초과했습니다.")))
          .onErrorResume(ConnectionLostException.class, ignored -> Mono.just(
              AgentConnectionControlResult.failure(request.requestId(), "UNAVAILABLE", "사용자 PC 연결이 끊겼습니다.")))
          .onErrorResume(TimeoutException.class, ignored -> Mono.just(
              AgentConnectionControlResult.failure(request.requestId(), "TIMEOUT", "연결 상태 확인 시간이 초과되었습니다.")))
          .onErrorResume(ignored -> Mono.just(
              AgentConnectionControlResult.failure(request.requestId(), "FAILED", "연결 요청을 완료하지 못했습니다.")))
          .doFinally(ignored -> {
            activeRequests.remove(request.requestId());
            completions.cancel(request.requestId());
            completions.close(request.requestId());
          });
    }).onErrorResume(TimeoutException.class, ignored -> Mono.just(
        AgentConnectionControlResult.failure(request.requestId(), "TIMEOUT", "연결 상태 확인 시간이 초과되었습니다.")))
        .onErrorResume(ignored -> Mono.just(
            AgentConnectionControlResult.failure(request.requestId(), "FAILED", "연결 요청을 완료하지 못했습니다.")));
  }

  public boolean complete(AgentConnectionControlResult result) {
    try {
      String content = objectMapper.writeValueAsString(result);
      return completions.complete(RelayExecutionResult.success(result.requestId(), content));
    } catch (Exception failure) {
      return completions.fail(result.requestId(), new IllegalStateException("connection result could not be encoded"));
    }
  }

  private AgentConnectionControlResult decodeConnectionControlResult(RelayExecutionResult response) {
    if (!response.success()) throw new IllegalStateException("agent connection control failed");
    try {
      return objectMapper.readValue(response.content(), AgentConnectionControlResult.class);
    } catch (Exception failure) {
      throw new IllegalStateException("agent connection result could not be decoded");
    }
  }

  public boolean complete(String requestId, String finalContent) {
    return completions.complete(requestId, finalContent);
  }

  public boolean complete(RelayExecutionResult result) {
    return completions.complete(result);
  }

  public boolean fail(String requestId, Throwable failure) {
    return completions.fail(requestId, failure);
  }

  private static Duration remaining(RelayExecutionRequest request) {
    return request.deadlineEpochMillis() == 0 ? Duration.ofDays(1)
        : Duration.ofMillis(request.deadlineEpochMillis() - System.currentTimeMillis());
  }

  private final class PayloadTooLargeException extends RuntimeException {
    private PayloadTooLargeException() {
      super("relay response exceeds configured UTF-8 payload limit");
    }
  }

  private int jsonBytes(Object value) {
    try {
      return objectMapper.writeValueAsBytes(value).length;
    } catch (Exception failure) {
      throw new IllegalArgumentException("relay payload cannot be encoded", failure);
    }
  }

  private Object lifecycleLock(UUID soloPlayerId) {
    return lifecycleLocks.computeIfAbsent(soloPlayerId, ignored -> new Object());
  }

  private record Connection(String connectionId, ConnectionLocationLease lease, AgentConnectionTransport transport) {
  }

  private record ActiveRequest(UUID soloPlayerId, String connectionId, Sinks.One<Throwable> connectionLost) {
  }
}
