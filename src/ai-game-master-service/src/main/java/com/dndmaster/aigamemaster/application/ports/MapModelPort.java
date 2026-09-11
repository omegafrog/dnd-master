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

    record MapBoundaryCandidate(int x, int y, String orientation, String kind,
                                double confidence, List<String> evidence, String source) {
        public MapBoundaryCandidate {
            if (x < 0 || y < 0) throw new IllegalArgumentException("boundary candidate coordinates must not be negative");
            if (!"HORIZONTAL".equals(orientation) && !"VERTICAL".equals(orientation)) {
                throw new IllegalArgumentException("boundary candidate orientation is invalid");
            }
            if (!"WALL".equals(kind) && !"DOOR".equals(kind)) {
                throw new IllegalArgumentException("boundary candidate kind is invalid");
            }
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("boundary candidate confidence must be between 0 and 1");
            }
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            source = source == null || source.isBlank() ? "IMAGE_RULES" : source.trim();
        }
    }

    /** 시나리오 진입 경로와 지도 출입구를 연결한 시작 칸 제안. */
    record PlayerStartProposal(String position, double confidence, List<String> evidence,
                               String source, String status) {
        public PlayerStartProposal(String position, double confidence, List<String> evidence, String source) {
            this(position, confidence, evidence, source, "PROPOSED");
        }
        public PlayerStartProposal {
            position = position == null ? "" : position.trim();
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("player start confidence must be between 0 and 1");
            }
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            source = source == null || source.isBlank() ? "SCENARIO_ENTRY" : source.trim();
            status = status == null || status.isBlank() ? "PROPOSED" : status.trim().toUpperCase(java.util.Locale.ROOT);
            if (!status.equals("PROPOSED") && !status.equals("UNRESOLVED")) {
                throw new IllegalArgumentException("player start proposal status is invalid");
            }
        }
    }

    record MapOutput(int width, int height, String structuredLayers,
                     List<String> obstacles, List<String> doors, List<String> boundaries, String playerStart,
                     List<MapBoundaryCandidate> candidates, PlayerStartProposal playerStartProposal) {
        public MapOutput(int width, int height, String structuredLayers,
                         List<String> obstacles, List<String> doors, List<String> boundaries, String playerStart,
                         List<MapBoundaryCandidate> candidates) {
            this(width, height, structuredLayers, obstacles, doors, boundaries, playerStart, candidates, null);
        }
        public MapOutput(int width, int height, String structuredLayers,
                         List<String> obstacles, List<String> doors, List<String> boundaries, String playerStart) {
            this(width, height, structuredLayers, obstacles, doors, boundaries, playerStart, List.of(), null);
        }
        public MapOutput(int width, int height, String structuredLayers, List<String> obstacles, List<String> doors, String playerStart) {
            this(width, height, structuredLayers, obstacles, doors, List.of(), playerStart, List.of(), null);
        }
        public MapOutput(int width, int height, String structuredLayers) {
            this(width, height, structuredLayers, List.of(), List.of(), List.of(), "", List.of(), null);
        }

        public MapOutput {
            if (width < 1 || height < 1) throw new IllegalArgumentException("map dimensions must be positive");
            structuredLayers = structuredLayers == null ? "" : structuredLayers.trim();
            obstacles = immutable(obstacles);
            doors = immutable(doors);
            boundaries = immutable(boundaries);
            playerStart = playerStart == null ? "" : playerStart.trim();
            candidates = List.copyOf(Objects.requireNonNull(candidates, "map boundary candidates must not be null"));
            if (playerStartProposal != null && playerStartProposal.position().isBlank()) playerStartProposal = null;
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
