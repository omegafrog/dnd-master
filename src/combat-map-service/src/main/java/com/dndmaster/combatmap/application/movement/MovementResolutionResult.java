package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MovementPath;
import java.util.List;

public record MovementResolutionResult(MovementPath requestedPath, List<GridPosition> traversedPath,
        GridPosition finalPosition, long mapVersion, List<String> publicEvents, String interruptionReason) {
    public MovementResolutionResult {
        traversedPath = List.copyOf(traversedPath);
        publicEvents = List.copyOf(publicEvents);
    }
}
