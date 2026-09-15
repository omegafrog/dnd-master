package com.dndmaster.relay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.reactive.server.WebTestClient.bindToController;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.dndmaster.relay.application.*;
import java.time.Duration;
import reactor.core.publisher.Mono;

class InternalExecutionControllerTest {
    @Test void rejectsMissingTokenAndReturnsFinalResultForAuthenticatedRequest() {
        var controller = new InternalExecutionController(request -> Mono.just(RelayExecutionResult.success(request.requestId(), "final")),
                request -> Mono.just(RelayExecutionResult.success(request.requestId(), "owned")), "secret", "relay-peer-secret");
        var client = bindToController(controller).controllerAdvice(new RelayExceptionHandler()).build();
        var request = new RelayExecutionRequest(UUID.randomUUID(), "r1", "w1", "private prompt", "model", "medium", "text", null, List.of());

        client.post().uri("/internal/executions").bodyValue(request).exchange().expectStatus().isUnauthorized();
        client.post().uri("/internal/executions").header("X-Internal-Token", "secret").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.requestId").isEqualTo("r1").jsonPath("$.content").isEqualTo("final");
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "wrong").bodyValue(request).exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "secret").bodyValue(request).exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "relay-peer-secret").bodyValue(request).exchange()
                .expectStatus().isForbidden();
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "relay-peer-secret")
                .header("X-Internal-Caller", "agent-connection-relay-service").header("X-Relay-Instance-Id", "relay-a")
                .bodyValue(request).exchange().expectStatus().isOk().expectBody().jsonPath("$.content").isEqualTo("owned");
    }

    @Test void recordsOwnedExecutionMetrics() {
        var metrics = new RecordingRelayMetrics();
        var controller = new InternalExecutionController(request -> Mono.just(RelayExecutionResult.success(request.requestId(), "final")),
                request -> Mono.just(RelayExecutionResult.success(request.requestId(), "owned")), "secret", "relay-peer-secret", metrics, "relay-c");
        var client = bindToController(controller).controllerAdvice(new RelayExceptionHandler()).build();
        var request = new RelayExecutionRequest(UUID.randomUUID(), "r-owned", "w1", "private prompt", "model", "medium", "text", null, List.of());

        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "relay-peer-secret")
                .header("X-Internal-Caller", "agent-connection-relay-service").header("X-Relay-Instance-Id", "relay-a")
                .bodyValue(request).exchange().expectStatus().isOk();
        assertNull(metrics.failureType);
        assertTrue(metrics.requestBytes > 0 && metrics.responseBytes > 0 && !metrics.duration.isZero());
    }

    private static final class RecordingRelayMetrics implements RelayMetrics {
        private long requestBytes;
        private long responseBytes;
        private Duration duration;
        private RelayFailureType failureType;

        @Override public void finished(long requestBytes, long responseBytes, Duration duration, RelayFailureType failureType) {
            this.requestBytes = requestBytes;
            this.responseBytes = responseBytes;
            this.duration = duration;
            this.failureType = failureType;
        }

        @Override public void activeConnections(int count) { }
    }
}
