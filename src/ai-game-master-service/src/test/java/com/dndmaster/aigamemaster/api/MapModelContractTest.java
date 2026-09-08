package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

    private static GmCompletionAdapter fixed(String response) {
        return new GmCompletionAdapter() {
            @Override
            public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse(response);
            }
        };
    }
}
