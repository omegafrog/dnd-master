package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import java.util.UUID;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RemoteAiExecutionPortTest {
    @Test
    void reports_a_typed_connection_unavailable_failure_without_a_local_fallback() {
        var result = new RemoteAiExecutionPort().execute(new AiExecutionRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000322"), "request-322", "work-322", "prompt",
                "gpt-5.6-luna", "medium", "TEXT", null, ""));

        assertThat(result).isInstanceOf(AiExecutionFailure.class);
        assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE);
    }

    @Test void sendsCompletedRequestToAuthenticatedRelayAndMapsFinalResult() throws Exception {
        var token = new java.util.concurrent.atomic.AtomicReference<String>();
        var body = new java.util.concurrent.atomic.AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/executions", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"requestId\":\"request-323\",\"content\":\"final\",\"failureType\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var port = new RemoteAiExecutionPort(HttpClient.newHttpClient(), new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort()), "service-token", Duration.ofSeconds(2));
            var result = port.execute(new AiExecutionRequest(UUID.randomUUID(), "request-323", "work-323", "completed prompt",
                    "model", "medium", "TEXT", null, ""));
            assertEquals("final", result.requireFinalText());
            assertEquals("service-token", token.get());
            assertTrue(body.get().contains("\"prompt\":\"completed prompt\""));
        } finally { server.stop(0); }
    }
}
