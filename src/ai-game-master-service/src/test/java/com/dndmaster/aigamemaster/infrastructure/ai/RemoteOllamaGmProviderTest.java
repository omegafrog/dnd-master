package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RemoteOllamaGmProviderTest {
    @Test
    void sendsJsonSchemaAsOllamaFormatObject() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"response\":\"{\\\"definitions\\\":[]}\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var schema = new ObjectMapper().readTree("{\"type\":\"object\",\"required\":[\"definitions\"],\"properties\":{\"definitions\":{\"type\":\"array\"}}}");
            var provider = new RemoteOllamaGmProvider(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "exaone3.5:7.8b", Duration.ofSeconds(5));
            assertEquals("{\"definitions\":[]}", provider.complete("op", "extract", text -> text, schema));
            var request = new ObjectMapper().readTree(body.get());
            assertTrue(request.path("format").isObject());
            assertEquals(schema, request.path("format"));
            assertEquals(32768, request.path("options").path("num_ctx").asInt());
            assertEquals("exaone3.5:7.8b", request.path("model").asText());
        } finally {
            server.stop(0);
        }
    }
}
