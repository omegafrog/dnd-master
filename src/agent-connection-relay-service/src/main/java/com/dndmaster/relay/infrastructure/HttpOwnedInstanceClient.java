package com.dndmaster.relay.infrastructure;

import com.dndmaster.relay.application.*;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class HttpOwnedInstanceClient implements OwnedInstanceClient {
    private final WebClient client;
    private final String internalToken;
    private final String instanceId;
    private final Duration timeout;
    public HttpOwnedInstanceClient(WebClient client, String internalToken, Duration timeout) {
        this(client, internalToken, "relay-instance", timeout);
    }
    public HttpOwnedInstanceClient(WebClient client, String internalToken, String instanceId, Duration timeout) {
        this.client = Objects.requireNonNull(client); this.internalToken = required(internalToken); this.instanceId = required(instanceId); this.timeout = Objects.requireNonNull(timeout);
    }
    @Override public Mono<RelayExecutionResult> execute(String internalAddress, RelayExecutionRequest request) {
        var endpoint = URI.create(internalAddress).resolve("/internal/owned-executions");
        return client.post().uri(endpoint).header("X-Internal-Token", internalToken).header("X-Relay-Instance-Id", instanceId)
                .bodyValue(request).retrieve().bodyToMono(RelayExecutionResult.class).timeout(timeout)
                .flatMap(result -> request.requestId().equals(result.requestId()) ? Mono.just(result)
                        : Mono.error(new IllegalStateException("owned instance returned a mismatched requestId")));
    }
    private static String required(String token) {
        if (token == null || token.isBlank()) throw new IllegalStateException("INTERNAL_SERVICE_TOKEN is required"); return token;
    }
}
