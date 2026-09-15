package com.dndmaster.relay.api;

import static org.springframework.test.web.reactive.server.WebTestClient.bindToController;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.dndmaster.relay.application.*;
import reactor.core.publisher.Mono;

class InternalExecutionControllerTest {
    @Test void rejectsMissingTokenAndReturnsFinalResultForAuthenticatedRequest() {
        var controller = new InternalExecutionController(request -> Mono.just(RelayExecutionResult.success(request.requestId(), "final")), "secret");
        var client = bindToController(controller).controllerAdvice(new RelayExceptionHandler()).build();
        var request = new RelayExecutionRequest(UUID.randomUUID(), "r1", "w1", "private prompt", "model", "medium", "text", null, List.of());

        client.post().uri("/internal/executions").bodyValue(request).exchange().expectStatus().isUnauthorized();
        client.post().uri("/internal/executions").header("X-Internal-Token", "secret").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.requestId").isEqualTo("r1").jsonPath("$.content").isEqualTo("final");
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "wrong").bodyValue(request).exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "secret").bodyValue(request).exchange()
                .expectStatus().isForbidden();
        client.post().uri("/internal/owned-executions").header("X-Internal-Token", "secret")
                .header("X-Internal-Caller", "agent-connection-relay-service").header("X-Relay-Instance-Id", "relay-a")
                .bodyValue(request).exchange().expectStatus().isOk().expectBody().jsonPath("$.failureType").isEqualTo("NO_CONNECTION");
    }
}
