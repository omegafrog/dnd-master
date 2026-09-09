package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Structured map evidence passed to the AI Game Master for a placement proposal. */
public record MapGenerationRequest(
        String selectedScenario,
        String currentContext,
        int gridWidth,
        int gridHeight,
        int cellSize,
        int distanceUnit,
        Collection<GridPosition> authoredObstacles,
        Collection<Door> authoredDoors,
        GridPosition authoredPlayerStart,
        MapImageEvidence mapImage,
        double gridOriginX,
        double gridOriginY,
        double gridCellSize,
        String crop,
        boolean gridConfirmed,
        String imageRevision,
        Collection<com.dndmaster.combatmap.domain.MapBoundary> authoredBoundaries) {
    public MapGenerationRequest {
        selectedScenario = required(selectedScenario, "selected scenario");
        currentContext = currentContext == null ? "" : currentContext.trim();
        if (gridWidth < 1 || gridHeight < 1 || cellSize < 1 || distanceUnit < 1) {
            throw new IllegalArgumentException("map geometry must be positive");
        }
        authoredObstacles = List.copyOf(Objects.requireNonNull(authoredObstacles, "authored obstacles must not be null"));
        authoredDoors = List.copyOf(Objects.requireNonNull(authoredDoors, "authored doors must not be null"));
        authoredObstacles.forEach(position -> validate(position, gridWidth, gridHeight, "authored obstacle"));
        authoredDoors.forEach(door -> validate(door.position(), gridWidth, gridHeight, "authored door"));
        if (authoredPlayerStart != null) validate(authoredPlayerStart, gridWidth, gridHeight, "authored player start");
        if (authoredDoors.stream().map(Door::position).anyMatch(authoredObstacles::contains)) {
            throw new IllegalArgumentException("authored door cannot be an obstacle");
        }
        if (authoredPlayerStart != null && (authoredObstacles.contains(authoredPlayerStart)
                || authoredDoors.stream().anyMatch(door -> door.position().equals(authoredPlayerStart)))) {
            throw new IllegalArgumentException("authored player start is blocked");
        }
        if (!Double.isFinite(gridOriginX) || !Double.isFinite(gridOriginY)
                || !Double.isFinite(gridCellSize) || gridCellSize <= 0) {
            throw new IllegalArgumentException("grid geometry must be finite and positive");
        }
        crop = crop == null ? "" : crop.trim();
        imageRevision = imageRevision == null ? "" : imageRevision.trim();
        authoredBoundaries = List.copyOf(Objects.requireNonNull(authoredBoundaries, "authored boundaries must not be null"));
        authoredBoundaries.forEach(boundary -> {
            if (boundary == null || !boundary.inside(new com.dndmaster.combatmap.domain.GridSpec(gridWidth, gridHeight, cellSize, distanceUnit))) {
                throw new IllegalArgumentException("authored boundary is outside the grid");
            }
        });
    }

    public MapGenerationRequest(String selectedScenario, String currentContext, int gridWidth, int gridHeight,
            int cellSize, int distanceUnit, Collection<GridPosition> authoredObstacles, Collection<Door> authoredDoors,
            GridPosition authoredPlayerStart, MapImageEvidence mapImage) {
        this(selectedScenario, currentContext, gridWidth, gridHeight, cellSize, distanceUnit,
                authoredObstacles, authoredDoors, authoredPlayerStart, mapImage, 0, 0, cellSize, "", false, "", List.of());
    }

    /** 요청자가 저장한 지도 격자와 자르기 범위를 고정해 AI에게 전달한다. */
    public MapGenerationRequest(String selectedScenario, String currentContext, int gridWidth, int gridHeight,
            int cellSize, int distanceUnit, Collection<GridPosition> authoredObstacles, Collection<Door> authoredDoors,
            GridPosition authoredPlayerStart, MapImageEvidence mapImage, double gridOriginX, double gridOriginY,
            double gridCellSize, String crop) {
        this(selectedScenario, currentContext, gridWidth, gridHeight, cellSize, distanceUnit,
                authoredObstacles, authoredDoors, authoredPlayerStart, mapImage,
                gridOriginX, gridOriginY, gridCellSize, crop, true, "", List.of());
    }

    /** 이전 호출부가 명시적으로 격자 확정 여부를 전달하던 생성자와의 호환성. */
    public MapGenerationRequest(String selectedScenario, String currentContext, int gridWidth, int gridHeight,
            int cellSize, int distanceUnit, Collection<GridPosition> authoredObstacles, Collection<Door> authoredDoors,
            GridPosition authoredPlayerStart, MapImageEvidence mapImage, double gridOriginX, double gridOriginY,
            double gridCellSize, String crop, boolean gridConfirmed) {
        this(selectedScenario, currentContext, gridWidth, gridHeight, cellSize, distanceUnit,
                authoredObstacles, authoredDoors, authoredPlayerStart, mapImage,
                gridOriginX, gridOriginY, gridCellSize, crop, gridConfirmed, "", List.of());
    }

    /** 저장된 이미지 버전과 사용자가 그린 선분을 함께 고정하는 감지 요청이다. */
    public MapGenerationRequest(String selectedScenario, String currentContext, int gridWidth, int gridHeight,
            int cellSize, int distanceUnit, Collection<GridPosition> authoredObstacles, Collection<Door> authoredDoors,
            GridPosition authoredPlayerStart, MapImageEvidence mapImage, double gridOriginX, double gridOriginY,
            double gridCellSize, String crop, String imageRevision,
            Collection<com.dndmaster.combatmap.domain.MapBoundary> authoredBoundaries) {
        this(selectedScenario, currentContext, gridWidth, gridHeight, cellSize, distanceUnit,
                authoredObstacles, authoredDoors, authoredPlayerStart, mapImage,
                gridOriginX, gridOriginY, gridCellSize, crop, true, imageRevision, authoredBoundaries);
    }

    public MapGenerationRequest(String selectedScenario, String currentContext) {
        this(selectedScenario, currentContext, 20, 20, 30, 5, List.of(), List.of(), null, null,
                0, 0, 30, "", false, "", List.of());
    }

    public MapGenerationRequest(String selectedScenario, String currentContext, int gridWidth, int gridHeight,
            int cellSize, int distanceUnit, Collection<GridPosition> authoredObstacles, Collection<Door> authoredDoors) {
        this(selectedScenario, currentContext, gridWidth, gridHeight, cellSize, distanceUnit,
                authoredObstacles, authoredDoors, null, null, 0, 0, cellSize, "", false, "", List.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static void validate(GridPosition position, int width, int height, String field) {
        if (position == null || position.x() >= width || position.y() >= height) {
            throw new IllegalArgumentException(field + " is outside the grid");
        }
    }
}
