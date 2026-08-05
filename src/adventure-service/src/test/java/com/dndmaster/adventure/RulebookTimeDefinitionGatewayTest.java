package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRulebookTimeDefinitionGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulebookTimeDefinitionGatewayTest {
    @Test
    void reads_time_from_locked_session_rulebook_source() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var configuration = new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), List.of(rulebookId), "engine", List.of("clock"), "start");
        var session = AdventureSession.create(new SessionId(sessionId), new OwnerPlayerId(ownerId),
                UUID.randomUUID(), 1, 1, configuration);
        AdventureSessionRepository sessions = new AdventureSessionRepository() {
            public Optional<AdventureSession> findById(SessionId id) { return id.equals(session.id()) ? Optional.of(session) : Optional.empty(); }
            public void save(AdventureSession value, long expectedVersion) {}
        };
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/rulebooks/" + rulebookId + "/source-preview", exchange -> {
            byte[] body = "{\"content\":\"{\\\"time\\\":{\\\"secondsPerTurn\\\":7}}\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            OptionalInt result = new CrossContextHttpRulebookTimeDefinitionGateway(sessions, HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), Duration.ofSeconds(2), new ObjectMapper()).apply(sessionId);
            assertEquals(7, result.orElseThrow());
        } finally {
            server.stop(0);
        }
    }
}
