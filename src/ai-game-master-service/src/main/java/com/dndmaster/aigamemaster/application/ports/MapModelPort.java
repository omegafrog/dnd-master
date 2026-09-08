package com.dndmaster.aigamemaster.application.ports;

import java.util.List;
import java.util.Objects;

/** Structured map placement proposal owned by the AI Game Master boundary. */
public interface MapModelPort {
    MapOutput generate(MapInput input);

    record MapInput(String selectedScenario, String currentContext, String mapData, String imageDataUri) {
        public MapInput(String selectedScenario, String currentContext) {
            this(selectedScenario, currentContext, "", "");
        }

        public MapInput(String selectedScenario, String currentContext, String mapData) {
            this(selectedScenario, currentContext, mapData, "");
        }

        public MapInput {
            selectedScenario = required(selectedScenario, "selected scenario");
            currentContext = required(currentContext, "current context");
            mapData = mapData == null ? "" : mapData.trim();
            imageDataUri = imageDataUri == null ? "" : imageDataUri.trim();
        }
    }

    record MapOutput(int width, int height, String structuredLayers,
                     List<String> obstacles, List<String> doors, List<String> boundaries, String playerStart) {
        public MapOutput(int width, int height, String structuredLayers, List<String> obstacles, List<String> doors, String playerStart) {
            this(width, height, structuredLayers, obstacles, doors, List.of(), playerStart);
        }
        public MapOutput(int width, int height, String structuredLayers) {
            this(width, height, structuredLayers, List.of(), List.of(), "");
        }

        public MapOutput {
            if (width < 1 || height < 1) throw new IllegalArgumentException("map dimensions must be positive");
            structuredLayers = structuredLayers == null ? "" : structuredLayers.trim();
            obstacles = immutable(obstacles);
            doors = immutable(doors);
            boundaries = immutable(boundaries);
            playerStart = playerStart == null ? "" : playerStart.trim();
        }
    }

    private static List<String> immutable(List<String> values) {
        return List.copyOf(Objects.requireNonNull(values, "map positions must not be null"));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
