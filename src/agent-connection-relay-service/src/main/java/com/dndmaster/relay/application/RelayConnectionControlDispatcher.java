package com.dndmaster.relay.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;

/** Routes control requests to the relay instance that owns the player's current agent lease. */
public final class RelayConnectionControlDispatcher implements RelayConnectionControlService {
    private final String instanceId;
    private final ConnectionLocationLookup locations;
    private final LocalConnectionExecutor local;
    private final OwnedInstanceClient remote;
    private final Duration timeout;

    public RelayConnectionControlDispatcher(String instanceId, ConnectionLocationLookup locations,
            LocalConnectionExecutor local, OwnedInstanceClient remote, Duration timeout) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.locations = Objects.requireNonNull(locations);
        this.local = Objects.requireNonNull(local);
        this.remote = Objects.requireNonNull(remote);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override
    public Mono<AgentConnectionControlResult> execute(ConnectionControlRequest request) {
        ConnectionControlRequest routed = request.deadlineEpochMillis() == 0
                ? request.withDeadline(System.currentTimeMillis() + timeout.toMillis()) : request;
        Duration remaining = Duration.ofMillis(routed.deadlineEpochMillis() - System.currentTimeMillis());
        if (remaining.isZero() || remaining.isNegative()) return Mono.just(
                AgentConnectionControlResult.failure(routed.requestId(), "TIMEOUT", "연결 요청 시간이 초과되었습니다."));
        return locations.find(routed.soloPlayerId())
                .flatMap(found -> found.<Mono<AgentConnectionControlResult>>map(lease -> {
                    ConnectionControlRequest targeted = routed.withConnectionId(lease.connectionId());
                    return instanceId.equals(lease.instanceId()) ? local.control(targeted)
                            : remote.control(lease.internalAddress(), targeted);
                }).orElseGet(() -> Mono.just(AgentConnectionControlResult.failure(
                        routed.requestId(), "UNAVAILABLE", "사용자 PC 연결을 사용할 수 없습니다."))))
                .timeout(remaining.compareTo(timeout) < 0 ? remaining : timeout)
                .onErrorResume(TimeoutException.class, ignored -> Mono.just(
                        AgentConnectionControlResult.failure(routed.requestId(), "TIMEOUT", "연결 요청 시간이 초과되었습니다.")))
                .onErrorResume(ignored -> Mono.just(
                        AgentConnectionControlResult.failure(routed.requestId(), "UNAVAILABLE", "사용자 PC 연결을 사용할 수 없습니다.")))
                .map(result -> routed.requestId().equals(result.requestId()) ? result
                        : AgentConnectionControlResult.failure(routed.requestId(), "FAILED", "연결 응답을 확인하지 못했습니다."));
    }
}
