package com.dndmaster.relay.api;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.application.*;
import com.dndmaster.relay.infrastructure.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import reactor.core.publisher.Mono;
import reactor.netty.*;
import reactor.netty.http.server.HttpServer;

@Testcontainers
class RelayEndToEndContractTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private LettuceConnectionFactory connectionFactory;
    @AfterEach void close() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test void authenticatedApiUsesRedisAndDirectOwnedInstanceHttpThenReturnsNoConnectionWithoutLease() {
        var receivedToken = new AtomicReference<String>();
        DisposableServer owner = HttpServer.create().port(0).handle((request, response) -> {
            receivedToken.set(request.requestHeaders().get("X-Internal-Token"));
            return response.header("Content-Type", "application/json")
                    .sendString(Mono.just("{\"requestId\":\"r323\",\"content\":\"final\",\"failureType\":null}"));
        }).bindNow();
        try {
            connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379)); connectionFactory.afterPropertiesSet();
            var template = new ReactiveStringRedisTemplate(connectionFactory, RedisSerializationContext.string());
            var locations = new RedisConnectionLocationRepository(template, Clock.systemUTC(), Duration.ofSeconds(1));
            var player = UUID.randomUUID();
            locations.renew(new ConnectionLocationLease(player, "relay-c", "http://localhost:" + owner.port(), "s", "c", Instant.now()), Duration.ofSeconds(30)).block();
            var dispatcher = new RelayExecutionDispatcher("relay-a", locations::find,
                    ignored -> Mono.error(new AssertionError("must use direct HTTP")),
                    new HttpOwnedInstanceClient(WebClient.create(), "secret", "relay-a", Duration.ofSeconds(2)),
                    RelayMetrics.noop(), Duration.ofSeconds(3));
            var client = WebTestClient.bindToController(new InternalExecutionController(dispatcher, "secret"))
                    .controllerAdvice(new RelayExceptionHandler()).build();
            var request = new RelayExecutionRequest(player, "r323", "w323", "completed prompt", "model", "medium", "text", null, List.of());
            client.post().uri("/internal/executions").header("X-Internal-Token", "secret").bodyValue(request).exchange()
                    .expectStatus().isOk().expectBody().jsonPath("$.content").isEqualTo("final");
            assertEquals("secret", receivedToken.get());
            locations.release(player, "c").block();
            client.post().uri("/internal/executions").header("X-Internal-Token", "secret").bodyValue(request).exchange()
                    .expectStatus().isOk().expectBody().jsonPath("$.failureType").isEqualTo("NO_CONNECTION");
        } finally { owner.disposeNow(); }
    }
}
