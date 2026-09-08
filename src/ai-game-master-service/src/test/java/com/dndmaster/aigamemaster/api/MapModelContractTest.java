package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapModelContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStructuredGmPlacementAndRejectsBlockedDoor() {
        GmCompletionAdapter adapter = fixed("{\"width\":4,\"height\":3,\"boundaries\":[\"1,1,VERTICAL,WALL\",\"2,1,HORIZONTAL,DOOR\"],\"obstacles\":[],\"doors\":[],\"playerStart\":\"0,0\",\"rationale\":\"visible room boundary\"}");
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(adapter, mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput("map", "room", "grid"));

        assertEquals(4, output.width());
        assertEquals(java.util.List.of(), output.obstacles());
        assertEquals(java.util.List.of(), output.doors());
        assertEquals(java.util.List.of("1,1,VERTICAL,WALL,false", "2,1,HORIZONTAL,DOOR,false"), output.boundaries());
        assertEquals("0,0", output.playerStart());
    }

    @Test
    void rejectsDoorThatOverlapsObstacle() {
        GmCompletionAdapter adapter = fixed("{\"width\":4,\"height\":3,\"obstacles\":[\"1,1\"],\"doors\":[\"1,1\"],\"playerStart\":\"0,0\"}");
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(adapter, mapper);

        assertThrows(IllegalArgumentException.class,
                () -> model.generate(new MapModelPort.MapInput("map", "room", "grid")));
    }

    @Test
    void usesAuthoredDimensionsWhenGmProviderIsUnavailable() {
        GmCompletionAdapter failing = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(failing, mapper);

        MapModelPort.MapOutput output = model.generate(
                new MapModelPort.MapInput("map", "room", "{\"gridWidth\":7,\"gridHeight\":6}"));

        assertEquals(7, output.width());
        assertEquals(6, output.height());
        assertEquals(java.util.List.of(), output.obstacles());
        assertEquals(java.util.List.of(), output.doors());
    }

    @Test
    void createsVisualBoundaryDraftWhenProviderTimesOut() throws Exception {
        BufferedImage image = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(java.awt.Color.BLACK);
        graphics.fillRect(15, 14, 10, 3);
        graphics.fillRect(24, 15, 3, 10);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());

        GmCompletionAdapter failing = new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        MapModelPort model = new AiGameMasterApiConfiguration().mapModelPort(failing, mapper);

        MapModelPort.MapOutput output = model.generate(new MapModelPort.MapInput(
                "map", "room",
                "{\"gridWidth\":4,\"gridHeight\":3,\"gridOriginX\":5,\"gridOriginY\":5,\"gridCellSize\":10,\"gridConfirmed\":true}",
                dataUri));

        assertEquals(4, output.width());
        assertEquals(3, output.height());
        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().contains("1,1,HORIZONTAL,WALL,false"));
        org.junit.jupiter.api.Assertions.assertTrue(output.boundaries().contains("2,1,VERTICAL,WALL,false"));
    }

    private static GmCompletionAdapter fixed(String response) {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        };
    }
}
