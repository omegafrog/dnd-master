package com.dndmaster.relay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.relay.infrastructure.InMemoryConnectionLocationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RelayConnectionControlDispatcherTest {
    @Test
    void routesToTheOwnerRelayAndCarriesTheCurrentConnectionId() {
        var repository = new InMemoryConnectionLocationRepository(Clock.systemUTC());
        UUID player = UUID.randomUUID();
        var owner = new ConnectionLocationLease(player, "relay-owner", "http://owner", "session", "install-2", Instant.now());
        repository.claim(owner, Duration.ofSeconds(30)).block();
        var routed = new AtomicReference<ConnectionControlRequest>();
        LocalConnectionExecutor local = ignored -> Mono.error(new AssertionError("must route to owner"));
        OwnedInstanceClient remote = new OwnedInstanceClient() {
            @Override public Mono<RelayExecutionResult> execute(String address, RelayExecutionRequest request) {
                return Mono.error(new AssertionError("wrong operation family"));
            }
            @Override public Mono<AgentConnectionControlResult> control(String address, ConnectionControlRequest request) {
                assertThat(address).isEqualTo("http://owner");
                routed.set(request);
                return Mono.just(new AgentConnectionControlResult("CONNECTION_CONTROL_RESULT", request.requestId(),
                        "CONNECTED", true, null, null, false, null, null));
            }
        };
        var dispatcher = new RelayConnectionControlDispatcher("relay-front", repository::find,
                local, remote, Duration.ofSeconds(2));

        var result = dispatcher.execute(new ConnectionControlRequest(player, "correlation", "STATUS", "", "", 0, ""))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("CONNECTED");
        assertThat(routed.get().connectionId()).isEqualTo("install-2");
    }
}
