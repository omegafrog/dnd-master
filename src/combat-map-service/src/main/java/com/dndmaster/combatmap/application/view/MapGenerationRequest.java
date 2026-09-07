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
        MapImageEvidence mapImage) {
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
    }

    public MapGenerationRequest(String selectedScenario, String currentContext) {
        this(selectedScenario, currentContext, 20, 20, 30, 5, List.of(), List.of(), null, null);
    }

    public MapGenerationRequest(String selectedScenario, String currentContext, int gridWidth, int gridHeight,
            int cellSize, int distanceUnit, Collection<GridPosition> authoredObstacles, Collection<Door> authoredDoors) {
        this(selectedScenario, currentContext, gridWidth, gridHeight, cellSize, distanceUnit,
                authoredObstacles, authoredDoors, null, null);
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
