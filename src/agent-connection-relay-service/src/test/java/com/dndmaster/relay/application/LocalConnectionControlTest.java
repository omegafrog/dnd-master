package com.dndmaster.relay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.relay.infrastructure.InMemoryConnectionLocationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalConnectionControlTest {
    @Test
    void routesConnectionControlThroughTheCurrentLeasedAgentAndCorrelatesTheResult() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository),
                new RequestCompletionRegistry(), RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "relay-a", "http://relay-a", "session", "install", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), new AgentConnectionTransport() {
            @Override public reactor.core.publisher.Mono<Void> send(RelayExecutionRequest request) {
                throw new AssertionError("connection control must use the typed control method");
            }
            @Override public reactor.core.publisher.Mono<Void> sendConnectionControl(AgentConnectionControlMessage sent) {
                assertThat(sent.action()).isEqualTo("START");
                assertThat(sent.operationType()).isEqualTo("SWITCH_ACCOUNT");
                assertThat(manager.complete(new AgentConnectionControlResult("CONNECTION_CONTROL_RESULT",
                        sent.requestId(), "AUTHENTICATING", true, "operation-1", "AUTHENTICATING", true,
                        "https://auth.example/approve", "브라우저에서 승인을 완료해 주세요."))).isTrue();
                return reactor.core.publisher.Mono.empty();
            }
        }).block();

        var result = manager.control(new ConnectionControlRequest(player, "correlation-1", "START",
                "operation-1", "SWITCH_ACCOUNT", System.currentTimeMillis() + 5_000, "install")).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("AUTHENTICATING");
        assertThat(result.authUrl()).isEqualTo("https://auth.example/approve");
        assertThat(result.requestId()).isEqualTo("correlation-1");
    }

    @Test
    void doesNotSendControlForAnotherInstallationConnection() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        var manager = new LocalConnectionManager(new ConnectionLeaseService(repository),
                new RequestCompletionRegistry(), RelayMetrics.noop(), Duration.ofSeconds(1));
        var player = UUID.randomUUID();
        var lease = new ConnectionLocationLease(player, "relay-a", "http://relay-a", "session", "install", Instant.now());
        manager.connect(lease, Duration.ofSeconds(30), new AgentConnectionTransport() {
            @Override public reactor.core.publisher.Mono<Void> send(RelayExecutionRequest request) {
                throw new AssertionError("wrong installation must not receive an execution message");
            }
            @Override public reactor.core.publisher.Mono<Void> sendConnectionControl(AgentConnectionControlMessage request) {
                throw new AssertionError("wrong installation must not receive the control message");
            }
        }).block();

        var result = manager.control(new ConnectionControlRequest(player, "wrong-install", "STATUS",
                "", "", System.currentTimeMillis() + 5_000, "other-install")).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
    }
}
