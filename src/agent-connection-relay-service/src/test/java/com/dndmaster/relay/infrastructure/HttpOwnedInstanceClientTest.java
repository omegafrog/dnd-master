package com.dndmaster.relay.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.application.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class HttpOwnedInstanceClientTest {
    @Test void expiredRequestDoesNotOpenAnInternalHttpHop() {
        var client = new HttpOwnedInstanceClient(WebClient.create(), "secret", "relay-a", Duration.ofSeconds(2));
        var request = new RelayExecutionRequest(UUID.randomUUID(), "expired", "w", "prompt", "model", "medium", "text", null,
                List.of(), System.currentTimeMillis() - 1);
        assertEquals(RelayFailureType.TIMEOUT, client.execute("http://127.0.0.1:1", request).block().failureType());
    }

    @Test void sendsOneAuthenticatedDirectHttpRequestToOwningInstance() {
        var token = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var relayInstance = new AtomicReference<String>();
        var caller = new AtomicReference<String>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            token.set(request.requestHeaders().get("X-Internal-Token"));
            path.set(request.uri());
            relayInstance.set(request.requestHeaders().get("X-Relay-Instance-Id"));
            caller.set(request.requestHeaders().get("X-Internal-Caller"));
            return response.header("Content-Type", "application/json")
                    .sendString(Mono.just("{\"requestId\":\"r1\",\"content\":\"final\",\"failureType\":null}"));
        }).bindNow();
        try {
            var client = new HttpOwnedInstanceClient(WebClient.create(), "service-secret", Duration.ofSeconds(2));
            var request = new RelayExecutionRequest(UUID.randomUUID(), "r1", "w1", "prompt", "model", "medium", "text", null, List.of());
            var result = client.execute("http://localhost:" + server.port(), request).block();
            assertTrue(result.success());
            assertEquals("service-secret", token.get());
            assertEquals("/internal/owned-executions", path.get());
            assertEquals("relay-instance", relayInstance.get());
            assertEquals("agent-connection-relay-service", caller.get());
        } finally { server.disposeNow(); }
    }
}
