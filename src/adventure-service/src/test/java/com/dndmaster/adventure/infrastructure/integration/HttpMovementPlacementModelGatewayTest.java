package com.dndmaster.adventure.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.CrossContextCallException;
import com.dndmaster.adventure.application.combat.MovementPlacementModelPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpMovementPlacementModelGatewayTest {
    @Test
    void translates_a_resolved_response_without_a_destination_to_a_typed_cross_context_failure() throws Exception {
        try (InvalidMovementPlacementServer server = new InvalidMovementPlacementServer()) {
            var gateway = new HttpMovementPlacementModelGateway(HttpClient.newHttpClient(), server.baseUri(), Duration.ofSeconds(2),
                    new ObjectMapper(), "internal-token");

            assertThrows(CrossContextCallException.class, () -> gateway.interpret(
                    new MovementPlacementModelPort.MovementPlacementContext("문으로 가", "public map", "0,0", "")));
        }
    }

    private static final class InvalidMovementPlacementServer implements AutoCloseable {
        private final HttpServer server;

        private InvalidMovementPlacementServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/internal/v1/gm/movement-placements", this::respond);
            server.start();
        }

        private URI baseUri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
        }

        private void respond(HttpExchange exchange) throws IOException {
            byte[] body = "{\"status\":\"RESOLVED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
