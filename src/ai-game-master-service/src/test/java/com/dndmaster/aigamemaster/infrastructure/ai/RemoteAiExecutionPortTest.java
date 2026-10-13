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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RemoteAiExecutionPortTest {
    @Test void preservesReportedUsageAndUnknownValuesFromRelay() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var responseBody = new java.util.concurrent.atomic.AtomicReference<>(
                "{\"requestId\":\"request-usage\",\"content\":\"final\",\"usage\":{\"inputTokens\":120,\"cachedInputTokens\":0,\"outputTokens\":9}}");
        server.createContext("/internal/executions", exchange -> {
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var port = new RemoteAiExecutionPort(HttpClient.newHttpClient(), new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));
            var request = new AiExecutionRequest(UUID.randomUUID(), "request-usage", "work", "prompt", "model", "medium", "TEXT", null, "");
            var reported = (com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess) port.execute(request);
            assertEquals(120L, reported.usage().inputTokens());
            assertEquals(0L, reported.usage().cachedInputTokens());
            assertEquals(9L, reported.usage().outputTokens());
            responseBody.set("{\"requestId\":\"request-usage\",\"content\":\"final\"}");
            var unknown = (com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess) port.execute(request);
            assertNull(unknown.usage().inputTokens());
            assertNull(unknown.usage().cachedInputTokens());
            assertNull(unknown.usage().outputTokens());
        } finally { server.stop(0); }
    }
    @Test
    void reports_a_typed_connection_unavailable_failure_without_a_local_fallback() {
        var result = new RemoteAiExecutionPort().execute(new AiExecutionRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000322"), "request-322", "work-322", "prompt",
                "gpt-5.6-luna", "medium", "TEXT", null, ""));

        assertThat(result).isInstanceOf(AiExecutionFailure.class);
        assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE);
    }

    @Test
    void maps_a_relay_http_failure_to_delivery_failed_and_logs_safe_stage_diagnostics(CapturedOutput output) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/executions", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var port = new RemoteAiExecutionPort(HttpClient.newHttpClient(), new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));
            var result = withDiagnostics(() -> port.execute(new AiExecutionRequest(UUID.randomUUID(), "request-503", "work-503", "prompt secret-marker",
                    "model", "medium", "TEXT", null, "")));

            assertThat(result).isInstanceOf(AiExecutionFailure.class);
            assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.DELIVERY_FAILED);
            assertThat(output.getAll()).contains("requestId=request-503", "stage=relay_http_response", "httpStatus=503",
                    "dispatchState=http_response_received", "dispatched=true", "relayHttpResponseReceived=true", "retrySafety=unknown");
            assertThat(output.getAll()).doesNotContain("secret-marker", "token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesTheReauthenticationRequiredResultFromTheLocalAgent(CapturedOutput output) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/executions", exchange -> {
            byte[] response = "{\"requestId\":\"request-reauth\",\"content\":\"\",\"failureType\":\"REAUTH_REQUIRED\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var port = new RemoteAiExecutionPort(HttpClient.newHttpClient(), new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));
            var result = (AiExecutionFailure) withDiagnostics(() -> port.execute(new AiExecutionRequest(UUID.randomUUID(), "request-reauth",
                    "work", "prompt", "model", "medium", "TEXT", null, "")));

            assertThat(result.reason()).isEqualTo(AiExecutionFailure.Reason.REAUTH_REQUIRED);
            assertThat(output.getAll()).contains("stage=relay_result", "relayFailureType=REAUTH_REQUIRED", "relayHttpResponseReceived=true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void logsRequestIdMismatchWithoutLoggingRelayPayload(CapturedOutput output) throws Exception {
        HttpServer server = serverWithBody("{\"requestId\":\"other-request\",\"content\":\"private response\"}");
        try {
            var port = port(server, Duration.ofSeconds(2));
            var result = withDiagnostics(() -> port.execute(request("request-mismatch", "private prompt")));
            assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.DELIVERY_FAILED);
            assertThat(output.getAll()).contains("requestId=request-mismatch", "stage=request_id_mismatch", "httpStatus=200",
                    "dispatchState=http_response_received", "dispatched=true", "relayHttpResponseReceived=true", "retrySafety=unknown");
            assertThat(output.getAll()).doesNotContain("private response", "private prompt", "other-request");
        } finally { server.stop(0); }
    }

    @Test
    void timeoutLogsExecutionAndRetryAsUnknown(CapturedOutput output) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/executions", exchange -> {
            try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(200, -1); } catch (java.io.IOException ignored) { /* client timed out */ }
            exchange.close();
        });
        server.start();
        try {
            var port = port(server, Duration.ofMillis(50));
            var result = withDiagnostics(() -> port.execute(request("request-timeout", "prompt")));
            assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.TIMEOUT);
            assertThat(output.getAll()).contains("stage=timeout", "exceptionClass=HttpTimeoutException", "dispatchState=dispatched_outcome_unknown", "dispatched=true",
                    "relayHttpResponseReceived=false", "retrySafety=unknown");
        } finally { server.stop(0); }
    }

    @Test
    void parseExceptionLogsClassAndWhetherResponseArrived(CapturedOutput output) throws Exception {
        HttpServer server = serverWithBody("not-json");
        try {
            var result = withDiagnostics(() -> port(server, Duration.ofSeconds(2)).execute(request("request-invalid-json", "private")));
            assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.DELIVERY_FAILED);
            assertThat(output.getAll()).contains("stage=exception", "exceptionClass=JsonParseException", "dispatchState=http_response_received", "dispatched=true",
                    "relayHttpResponseReceived=true", "retrySafety=unknown");
            assertThat(output.getAll()).doesNotContain("not-json", "private");
        } finally { server.stop(0); }
    }

    private static HttpServer serverWithBody(String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/executions", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static RemoteAiExecutionPort port(HttpServer server, Duration timeout) {
        return new RemoteAiExecutionPort(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://localhost:" + server.getAddress().getPort()), "token", timeout);
    }

    private static AiExecutionRequest request(String requestId, String prompt) {
        return new AiExecutionRequest(UUID.randomUUID(), requestId, "work", prompt, "model", "medium", "TEXT", null, "");
    }

    private static <T> T withDiagnostics(java.util.concurrent.Callable<T> action) throws Exception {
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try { return action.call(); }
        finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
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
            var registry = new SimpleMeterRegistry();
            var port = new RemoteAiExecutionPort(HttpClient.newHttpClient(), new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort()), "service-token", Duration.ofSeconds(2), registry);
            var schema = new ObjectMapper().readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}");
            var result = port.execute(new AiExecutionRequest(UUID.randomUUID(), "request-323", "work-323", "completed prompt",
                    "model", "medium", "JSON", schema, "", null,
                    java.util.List.of(new AiExecutionRequest.Subtask("stage-a", "child prompt", schema))));
            assertEquals("final", result.requireFinalText());
            assertEquals("service-token", token.get());
            assertTrue(body.get().contains("\"prompt\":\"completed prompt\""));
            var sent = new ObjectMapper().readTree(body.get());
            assertThat(sent.path("subtasks").get(0).path("stageId").asText()).isEqualTo("stage-a");
            assertThat(sent.path("subtasks").get(0).path("prompt").asText()).isEqualTo("child prompt");
            assertThat(sent.path("subtasks").get(0).path("outputSchema")).isEqualTo(schema);
            assertEquals(1, registry.get("ai.execution.count").tag("implementation", "remote-relay").tag("result", "success").counter().count());
            assertEquals(1, registry.get("ai.execution.duration").tag("implementation", "remote-relay").tag("result", "success").timer().count());
        } finally { server.stop(0); }
    }
}
