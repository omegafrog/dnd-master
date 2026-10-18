package com.dndmaster.relay.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpScenarioElementLookupTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ability_lookup_forwards_confirmed_session_adventure_and_character_scope() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        AtomicReference<JsonNode> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/adventures/abilities/lookup", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("internal-token");
            received.set(mapper.readTree(exchange.getRequestBody()));
            byte[] body = "{\"status\":\"NEEDS_PREPARATION\",\"definition\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var lookup = new HttpScenarioElementLookup(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), Duration.ofSeconds(2),
                    "internal-token", mapper);
            JsonNode scope = mapper.createObjectNode().put("ownerId", ownerId.toString())
                    .put("sessionId", sessionId.toString()).put("adventureId", adventureId.toString())
                    .put("characterSheetId", sheetId.toString());

            JsonNode response = lookup.lookupAbility(ownerId, scope, "ability:blink");

            assertThat(response.path("status").asText()).isEqualTo("NEEDS_PREPARATION");
            assertThat(received.get().path("ownerId").asText()).isEqualTo(ownerId.toString());
            assertThat(received.get().path("sessionId").asText()).isEqualTo(sessionId.toString());
            assertThat(received.get().path("adventureId").asText()).isEqualTo(adventureId.toString());
            assertThat(received.get().path("characterSheetId").asText()).isEqualTo(sheetId.toString());
            assertThat(received.get().path("abilityId").asText()).isEqualTo("ability:blink");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ability_lookup_rejects_owner_mismatch_and_incomplete_confirmed_scope_before_http() {
        UUID ownerId = UUID.randomUUID();
        var lookup = new HttpScenarioElementLookup(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1/"),
                Duration.ofMillis(100), "internal-token", mapper);

        assertThatThrownBy(() -> lookup.lookupAbility(ownerId,
                mapper.createObjectNode().put("ownerId", UUID.randomUUID().toString()), "ability:blink"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> lookup.lookupAbility(ownerId,
                mapper.createObjectNode().put("ownerId", ownerId.toString()).put("sessionId", UUID.randomUUID().toString()),
                "ability:blink"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
