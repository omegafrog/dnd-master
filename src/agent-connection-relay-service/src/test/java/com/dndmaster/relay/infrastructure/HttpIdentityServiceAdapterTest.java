package com.dndmaster.relay.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.relay.infrastructure.HttpIdentityServiceAdapter.IdentityServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpIdentityServiceAdapterTest {
    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Test
    void introspectsBearerTokenThroughIdentityAccessService() throws Exception {
        try (StubServer server = new StubServer(200, "{\"authenticated\":true,\"playerId\":\"" + PLAYER_ID + "\"}")) {
            var adapter = adapter(server.uri());

            assertEquals(PLAYER_ID, adapter.introspectUser("session-token"));
            assertEquals("POST", server.method);
            assertEquals("/internal/v1/auth/introspections", server.path);
            assertEquals("{\"token\":\"session-token\"}", server.body);
            assertEquals("relay-token", server.internalToken);
        }
    }

    @Test
    void rejectsUnauthenticatedResponse() throws Exception {
        try (StubServer server = new StubServer(200, "{\"authenticated\":false,\"playerId\":null}")) {
            var adapter = adapter(server.uri());

            assertThrows(IdentityServiceException.class, () -> adapter.introspectUser("expired-token"));
        }
    }

    @Test
    void mapsIdentityServiceFailureToAdapterException() throws Exception {
        try (StubServer server = new StubServer(503, "provider failure")) {
            var adapter = adapter(server.uri());

            assertThrows(IdentityServiceException.class, () -> adapter.introspectUser("session-token"));
        }
    }

    private static HttpIdentityServiceAdapter adapter(URI baseUri) {
        return new HttpIdentityServiceAdapter(
                HttpClient.newHttpClient(), baseUri, Duration.ofSeconds(2), new ObjectMapper(), "relay-token");
    }

    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;
        private final int status;
        private final String response;
        private volatile String method;
        private volatile String path;
        private volatile String body;
        private volatile String internalToken;

        private StubServer(int status, String response) throws IOException {
            this.status = status;
            this.response = response;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/", exchange -> {
                method = exchange.getRequestMethod();
                path = exchange.getRequestURI().getPath();
                internalToken = exchange.getRequestHeaders().getFirst("X-Internal-Token");
                body = new String(exchange.getRequestBody().readAllBytes());
                byte[] bytes = response.getBytes();
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            this.server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
