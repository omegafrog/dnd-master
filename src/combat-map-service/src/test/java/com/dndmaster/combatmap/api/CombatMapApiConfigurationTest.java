package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.view.DetectedMapGrid;
import com.dndmaster.combatmap.application.view.MapGridDetectionPort;
import com.dndmaster.combatmap.application.view.HttpAiMapGenerationGateway;
import com.dndmaster.combatmap.application.view.MapGenerationRequest;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.application.view.PreparedMapData;
import com.dndmaster.combatmap.application.view.UploadedMapSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class CombatMapApiConfigurationTest {
    @Test
    void internalTokenIsRequiredAtConfigurationBoundary() {
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> new CombatMapApiConfiguration().combatMapApiRequestGuard(""));
    }

    @Test
    void aiGameMasterProposalBecomesValidatedPreparedMapData() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/gm/maps", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = "{\"width\":4,\"height\":3,\"boundaries\":[\"1,1,VERTICAL,WALL,false\",\"2,1,HORIZONTAL,DOOR,false\"],\"obstacles\":[],\"doors\":[],\"playerStart\":\"0,0\",\"rationale\":\"source-backed layout\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var gateway = new HttpAiMapGenerationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(5), new ObjectMapper(), "token");
            PreparedMapData data = gateway.generate(new MapGenerationRequest("map", "context", 4, 3, 30, 5,
                    java.util.List.of(), java.util.List.of(), new com.dndmaster.combatmap.domain.GridPosition(0, 0),
                    new com.dndmaster.combatmap.application.view.MapImageEvidence("image/png", new byte[] {1, 2, 3})));
            assertEquals(new GridSpec(4, 3, 30, 5), data.grid());
            assertTrue(data.obstacles().isEmpty());
            assertTrue(data.doors().isEmpty());
            assertTrue(data.layers().stream().anyMatch(layer -> layer.type().equals("MAP_BOUNDARIES") && layer.value().contains("1,1,VERTICAL,WALL,false")));
            assertTrue(data.layers().stream().noneMatch(layer -> layer.type().equals("GM_PLAYER_START")));
            assertTrue(data.layers().stream().anyMatch(layer -> layer.type().equals("MAP_IMAGE") && layer.value().startsWith("data:image/png;base64,")));
            JsonNode request = new ObjectMapper().readTree(requestBody.get());
            assertEquals("0,0", new ObjectMapper().readTree(request.path("mapData").asText()).path("authoredPlayerStart").asText());
            assertTrue(request.path("imageDataUri").asText().startsWith("data:image/png;base64,"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsConfirmedGridGeometryToTheAiWallDetectionContract() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/gm/maps", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = "{\"width\":4,\"height\":3,\"boundaries\":[],\"obstacles\":[],\"doors\":[],\"playerStart\":\"\",\"candidates\":[{\"x\":1,\"y\":1,\"orientation\":\"HORIZONTAL\",\"kind\":\"WALL\",\"confidence\":0.75,\"evidence\":[\"continuous-edge\"],\"source\":\"IMAGE_RULES\"}]}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var gateway = new HttpAiMapGenerationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(5), new ObjectMapper(), "token");
            PreparedMapData generated = gateway.generate(new MapGenerationRequest("벽·문 감지", "confirmed", 4, 3, 30, 5,
                    java.util.List.of(), java.util.List.of(), null,
                    new com.dndmaster.combatmap.application.view.MapImageEvidence("image/png", new byte[] {1}),
                    112.5, 48.25, 31.75, "100,40,900,700", "revision-1",
                    java.util.List.of(new com.dndmaster.combatmap.domain.MapBoundary(1, 1,
                            com.dndmaster.combatmap.domain.MapBoundary.Orientation.VERTICAL,
                            com.dndmaster.combatmap.domain.MapBoundary.Kind.WALL, false))));
            JsonNode mapData = new ObjectMapper().readTree(new ObjectMapper().readTree(requestBody.get()).path("mapData").asText());
            assertEquals(112.5, mapData.path("gridOriginX").asDouble());
            assertEquals(48.25, mapData.path("gridOriginY").asDouble());
            assertEquals(31.75, mapData.path("gridCellSize").asDouble());
            assertEquals("100,40,900,700", mapData.path("crop").asText());
            assertEquals("revision-1", mapData.path("imageRevision").asText());
            assertEquals("1,1,VERTICAL,WALL,false", mapData.path("authoredBoundaries").get(0).asText());
            assertEquals(1, generated.candidates().size());
            assertTrue(generated.layers().stream().anyMatch(layer -> layer.type().equals("GRID_BOUNDS")
                    && layer.value().startsWith("112.5,48.25,127.0,95.25,")));
            assertTrue(generated.layers().stream().anyMatch(layer -> layer.type().equals("MAP_BOUNDARIES")
                    && layer.value().contains("1,1,VERTICAL,WALL,false")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesEntryStatusAndNarrativeContextInsteadOfAutoSelectingAmbiguousCandidates() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/gm/map-entry-placement", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"status\":\"AMBIGUOUS\",\"entryInterpretation\":{\"transition\":\"DESCEND_STAIRS\"},"
                    + "\"candidates\":[{\"exitPoint\":{\"xNormalized\":0.5,\"yNormalized\":0.4},\"confidence\":0.71,\"source\":\"MAP_IMAGE\"},"
                    + "{\"exitPoint\":{\"xNormalized\":0.5,\"yNormalized\":0.7},\"confidence\":0.69,\"source\":\"MAP_IMAGE\"}],\"reason\":\"two stairs\"}").getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var gateway = new HttpAiMapGenerationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(5), new ObjectMapper(), "token");
            PreparedMapData generated = gateway.proposeEntryPlacement(new MapGenerationRequest(
                    "맵 진입", "scene=저장고;location=지하 저장고", 4, 3, 30, 5,
                    java.util.List.of(), java.util.List.of(), null, null)
                    .withEntryEvidence("계단을 내려갑니다", "계단 진입", "계단 끝에 도착합니다")
                    .withEntryContext("양조장 뒤편에서 계단을 발견했습니다", "지하 저장고"));

            JsonNode request = new ObjectMapper().readTree(requestBody.get());
            assertEquals("지하 저장고", request.path("location").asText());
            assertEquals("양조장 뒤편에서 계단을 발견했습니다", request.path("firstNarration").asText());
            assertTrue(generated.layers().stream().noneMatch(layer -> layer.type().equals("GM_PLAYER_START_PROPOSAL")));
            assertTrue(generated.layers().stream().anyMatch(layer -> layer.type().equals("GM_ENTRY_PLACEMENT_RESULT")
                    && layer.value().contains("AMBIGUOUS") && layer.value().contains("\"xNormalized\":0.5")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void projectsResolvedImageAnchorWithConfirmedGridGeometry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/gm/map-entry-placement", exchange -> {
            byte[] response = ("{\"status\":\"RESOLVED\",\"entryInterpretation\":{\"transition\":\"DESCEND_STAIRS\"},"
                    + "\"candidates\":[{\"exitPoint\":{\"xNormalized\":0.35,\"yNormalized\":0.35},\"confidence\":0.91,"
                    + "\"source\":\"MAP_IMAGE\",\"evidence\":[\"계단 끝\"]}]}").getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var gateway = new HttpAiMapGenerationGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(5), new ObjectMapper(), "token");
            PreparedMapData generated = gateway.proposeEntryPlacement(new MapGenerationRequest(
                    "맵 진입", "scene=저장고", 4, 3, 30, 5, java.util.List.of(), java.util.List.of(), null,
                    png(200, 100), 20, 10, 20, "20,10,80,60"));

            assertTrue(generated.layers().stream().anyMatch(layer -> layer.type().equals("GM_PLAYER_START_PROPOSAL")
                    && layer.value().contains("\"position\":\"2,1\"")));
            assertTrue(generated.layers().stream().anyMatch(layer -> layer.type().equals("GM_ENTRY_PLACEMENT_RESULT")
                    && layer.value().contains("\"projectedCandidates\":[{\"x\":2,\"y\":1")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void uploadedMapPreparationDelegatesGridGeometryToPreprocessingPort() throws Exception {
        var image = new BufferedImage(32, 40, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MapGridDetectionPort detector = ignored -> java.util.Optional.of(new DetectedMapGrid(2, 3, 8, 4, 6, 1.0));

        PreparedMapData prepared = new CombatMapApiConfiguration().mapFilePreparationPort(detector)
                .prepare(new UploadedMapSource("map.png", bytes.toByteArray()));

        assertEquals(new GridSpec(2, 3, 8, 5), prepared.grid());
        assertEquals("4,6,16,24,32,40", prepared.layers().stream()
                .filter(layer -> layer.type().equals("GRID_BOUNDS"))
                .findFirst().orElseThrow().value());
    }

    private static com.dndmaster.combatmap.application.view.MapImageEvidence png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new com.dndmaster.combatmap.application.view.MapImageEvidence("image/png", bytes.toByteArray());
    }

}
