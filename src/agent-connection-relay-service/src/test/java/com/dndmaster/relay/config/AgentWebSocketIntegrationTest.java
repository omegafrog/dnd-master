package com.dndmaster.relay.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.dndmaster.relay.application.IdentityServicePort;
import com.dndmaster.relay.application.RelayExecutionRequest;
import com.dndmaster.relay.application.RelayExecutionResult;
import com.dndmaster.relay.application.ConnectionLocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "INTERNAL_SERVICE_TOKEN=test-token",
                "relay.instance-id=test-relay",
                "relay.internal-address=http://127.0.0.1:8080",
                "relay.execution-timeout=PT10S"
        })
class AgentWebSocketIntegrationTest {
    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConnectionLocationRepository locations;

    @MockBean
    IdentityServicePort identityService;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void receivesExecutionOverWebSocketAndReturnsResultToWaitingHttpRequest() throws Exception {
        when(identityService.introspectUser("agent-token")).thenReturn(PLAYER_ID);
        var connected = Sinks.<Void>one();
        var receivedRequest = Sinks.<RelayExecutionRequest>one();
        var webSocketClient = new ReactorNettyWebSocketClient();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("agent-token");

        Mono<Void> agent = webSocketClient.execute(
                URI.create("ws://127.0.0.1:" + port + "/ws/agent"),
                headers,
                session -> {
                    connected.tryEmitEmpty();
                    var responseToSend = Sinks.<String>one();
                    var responded = new AtomicBoolean();
                    Mono<Void> send = session.send(responseToSend.asMono().map(session::textMessage));
                    Mono<Void> receive = session.receive()
                            .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                            .flatMap(message -> Mono.fromCallable(() -> objectMapper.readValue(
                                            message.getPayloadAsText(), RelayExecutionRequest.class))
                                    .filter(request -> responded.compareAndSet(false, true)))
                            .flatMap(request -> {
                                receivedRequest.tryEmitValue(request);
                                return Mono.fromCallable(() -> objectMapper.writeValueAsString(
                                                RelayExecutionResult.success(request.requestId(), "codex result")))
                                        .doOnNext(responseToSend::tryEmitValue)
                                        .then();
                            })
                            .then();
                    return Mono.when(send, receive);
                });
        Disposable agentSubscription = agent.subscribe();
        try {
            connected.asMono().block(Duration.ofSeconds(10));
            Mono.defer(() -> locations.find(PLAYER_ID))
                    .filter(location -> location.isPresent())
                    .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(50)))
                    .block(Duration.ofSeconds(10));

            RelayExecutionRequest request = new RelayExecutionRequest(
                    PLAYER_ID, "request-1", "operation-1", "run codex", "gpt-5", "medium", "text", null, List.of());
            RelayExecutionResult result = WebClient.create("http://127.0.0.1:" + port)
                    .post()
                    .uri("/internal/executions")
                    .header("X-Internal-Token", "test-token")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RelayExecutionResult.class)
                    .block(Duration.ofSeconds(10));

            RelayExecutionRequest sent = receivedRequest.asMono().block(Duration.ofSeconds(10));
            assertTrue(result.success());
            assertEquals("request-1", result.requestId());
            assertEquals("codex result", result.content());
            assertEquals("run codex", sent.prompt());
            assertEquals("request-1", sent.requestId());
        } finally {
            agentSubscription.dispose();
        }
    }
}
