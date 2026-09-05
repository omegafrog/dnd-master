package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Objects;
import java.util.Optional;

public record MapActivationContext(int stagePosition, Optional<GridPosition> spawnCandidate, Optional<EntrySide> entrySide) {
    public MapActivationContext {
        if (stagePosition <= 0) throw new IllegalArgumentException("stage position must be positive");
        spawnCandidate = Objects.requireNonNull(spawnCandidate);
        entrySide = Objects.requireNonNull(entrySide);
    }
    public static MapActivationContext atStage(int stagePosition) {
        return new MapActivationContext(stagePosition, Optional.empty(), Optional.empty());
    }
    public enum EntrySide { NORTH, EAST, SOUTH, WEST }
}
