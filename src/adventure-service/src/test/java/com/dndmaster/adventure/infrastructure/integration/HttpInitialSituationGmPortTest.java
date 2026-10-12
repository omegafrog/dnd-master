package com.dndmaster.adventure.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dndmaster.adventure.application.scenario.preparation.InitialSituationGmPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationAgentFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpInitialSituationGmPortTest {
    @Test
    void initial_situation_request_requires_an_object_schema() {
        assertThatThrownBy(() -> new InitialSituationGmPort.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "openai", "story-model", "high", "initial-situation:missing-schema", "prompt", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InitialSituationGmPort.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "openai", "story-model", "high", "initial-situation:invalid-schema", "prompt",
                new ObjectMapper().createObjectNode().put("type", "object")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sends_an_authenticated_selected_provider_request_and_returns_structured_output() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID endpoint = UUID.randomUUID();
        var schema = new ObjectMapper().readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}");
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        var token = new java.util.concurrent.atomic.AtomicReference<String>();
        try (var server = new InitialSituationServer(requestBody, token, 200,
                "{\"stages\":[{\"stageId\":\"arrival\"}]}")) {
            var port = new HttpInitialSituationGmPort(HttpClient.newHttpClient(), server.baseUri(),
                    Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
            var request = new InitialSituationGmPort.Request(owner, UUID.randomUUID(), endpoint,
                    "codex-cli", "story-model", "ultra", "initial-situation:adventure:backbone", "source-grounded prompt", schema, true,
                    java.util.List.of(new InitialSituationGmPort.StageTask("arrival", "arrival prompt", schema),
                            new InitialSituationGmPort.StageTask("climax", "climax prompt", schema)));

            var response = port.generate(request);

            assertThat(response.path("stages").get(0).path("stageId").asText()).isEqualTo("arrival");
            assertThat(token.get()).isEqualTo("internal-token");
            var sent = new ObjectMapper().readTree(requestBody.get());
            assertThat(sent.path("soloPlayerId").asText()).isEqualTo(owner.toString());
            assertThat(sent.path("endpointId").asText()).isEqualTo(endpoint.toString());
            assertThat(sent.path("provider").asText()).isEqualTo("codex-cli");
            assertThat(sent.path("model").asText()).isEqualTo("story-model");
            assertThat(sent.path("reasoning").asText()).isEqualTo("ultra");
            assertThat(sent.path("prompt").asText()).contains("source-grounded prompt");
            assertThat(sent.path("outputSchema")).isEqualTo(schema);
            assertThat(sent.path("useSubagents").asBoolean()).isTrue();
            assertThat(sent.path("stageTasks").get(0).path("stageId").asText()).isEqualTo("arrival");
            assertThat(sent.path("stageTasks").get(0).path("prompt").asText()).isEqualTo("arrival prompt");
            assertThat(sent.path("stageTasks").get(1).path("outputSchema")).isEqualTo(schema);
        }
    }

    @Test
    void keeps_provider_failures_as_failures() throws Exception {
        try (var server = new InitialSituationServer(new java.util.concurrent.atomic.AtomicReference<>(),
                new java.util.concurrent.atomic.AtomicReference<>(), 503,
                "{\"code\":\"AI_EXECUTION_CONNECTION_UNAVAILABLE\",\"correlationId\":\"initial-situation:retry\",\"retryable\":true}")) {
            var port = new HttpInitialSituationGmPort(HttpClient.newHttpClient(), server.baseUri(),
                    Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
            var request = new InitialSituationGmPort.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "openai", "story-model", "high", "initial-situation:retry", "prompt",
                    new ObjectMapper().readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}"));

            assertThatThrownBy(() -> port.generate(request)).isInstanceOf(ScenarioCompilationAgentFailureException.class)
                    .hasMessageNotContaining("prompt");
        }
    }

    private static final class InitialSituationServer implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.atomic.AtomicReference<String> body;
        private final java.util.concurrent.atomic.AtomicReference<String> token;

        private InitialSituationServer(java.util.concurrent.atomic.AtomicReference<String> body,
                java.util.concurrent.atomic.AtomicReference<String> token, int status, String response) throws Exception {
            this.body = body;
            this.token = token;
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/internal/gm/initial-situation", exchange -> {
                this.body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                this.token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
        }

        private URI baseUri() { return URI.create("http://localhost:" + server.getAddress().getPort() + "/"); }
        @Override public void close() { server.stop(0); }
    }
}
