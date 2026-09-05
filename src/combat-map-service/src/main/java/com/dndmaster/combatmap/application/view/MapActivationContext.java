package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MapActivationContext(int stagePosition, Optional<GridPosition> spawnCandidate, Optional<EntrySide> entrySide,
        Optional<UUID> playerTokenId, UUID situationId, long situationRevision, int turnIndex,
        String currentScene, String location) {
    public MapActivationContext {
        if (stagePosition <= 0) throw new IllegalArgumentException("stage position must be positive");
        spawnCandidate = Objects.requireNonNull(spawnCandidate);
        entrySide = Objects.requireNonNull(entrySide);
        playerTokenId = Objects.requireNonNull(playerTokenId);
        situationId = Objects.requireNonNull(situationId);
        if (situationRevision < 1) throw new IllegalArgumentException("situation revision must be positive");
        if (turnIndex < 0) throw new IllegalArgumentException("turn index must not be negative");
        currentScene = required(currentScene, "current scene");
        location = required(location, "location");
    }
    public MapActivationContext(int stagePosition, Optional<GridPosition> spawnCandidate, Optional<EntrySide> entrySide) {
        this(stagePosition, spawnCandidate, entrySide, Optional.empty(), UUID.randomUUID(), 1, 0, "unknown", "unknown");
    }
    public static MapActivationContext atStage(int stagePosition) {
        return new MapActivationContext(stagePosition, Optional.empty(), Optional.empty());
    }
    public static MapActivationContext from(int stagePosition, Optional<GridPosition> spawnCandidate,
            Optional<EntrySide> entrySide, Optional<UUID> playerTokenId, UUID situationId,
            long situationRevision, int turnIndex, String currentScene, String location) {
        return new MapActivationContext(stagePosition, spawnCandidate, entrySide, playerTokenId,
                situationId, situationRevision, turnIndex, currentScene, location);
    }
    public enum EntrySide { NORTH, EAST, SOUTH, WEST }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
