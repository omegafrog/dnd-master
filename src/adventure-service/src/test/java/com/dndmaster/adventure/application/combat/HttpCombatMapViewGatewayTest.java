package com.dndmaster.adventure.application.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpCombatMapViewGatewayTest {
    @Test
    void readsPersistedHiddenFeaturesFromTheInternalGmMapView() throws Exception {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/adventures/", exchange -> {
            String payload = "{\"mapId\":\"" + mapId + "\",\"grid\":{\"width\":10,\"height\":10,\"cellSize\":1,\"distanceUnit\":5},"
                    + "\"tokens\":[],\"obstacles\":[],\"doors\":[],\"layers\":[],\"current\":[],\"explored\":[],\"version\":3,"
                    + "\"playerStartCandidates\":[],\"spatialFeatures\":[]}";
            respond(exchange, payload);
        });
        server.createContext("/internal/v1/combat-maps/", exchange -> respond(exchange,
                "{\"mapId\":\"" + mapId + "\",\"tokens\":[],\"obstacles\":[],\"layers\":[],\"version\":3,"
                        + "\"spatialPreparationBlocked\":false,\"spatialFeatures\":[{\"id\":\"" + UUID.randomUUID()
                        + "\",\"type\":\"TRAP\",\"cells\":[{\"x\":8,\"y\":5}],\"visibility\":\"HIDDEN\","
                        + "\"state\":\"HIDDEN\",\"origin\":\"AI_GENERATED\",\"sourceReference\":\"\"}]}"));
        server.start();
        try {
            var gateway = new HttpCombatMapViewGateway(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), Duration.ofSeconds(2),
                    new ObjectMapper(), "internal-token");

            UUID resolvedMapId = gateway.preparationView(adventureId, ownerId).orElseThrow().mapId();
            var features = gateway.gmSpatialFeatures(resolvedMapId, ownerId);

            assertThat(resolvedMapId).isEqualTo(mapId);
            assertThat(features).singleElement().satisfies(feature -> {
                assertThat(feature.visibility()).isEqualTo("HIDDEN");
                assertThat(feature.type()).isEqualTo("TRAP");
                assertThat(feature.cells()).containsExactly(new CombatMapViewPort.Position(8, 5));
            });
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String response) throws java.io.IOException {
        byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }
}
