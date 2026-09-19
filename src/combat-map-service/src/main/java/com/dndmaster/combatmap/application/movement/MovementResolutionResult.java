package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MovementPath;
import java.util.List;

public record MovementResolutionResult(MovementPath requestedPath, List<GridPosition> traversedPath,
        GridPosition finalPosition, long mapVersion, List<String> publicEvents, String interruptionReason,
        MovementResolutionOutcomeStatus status, java.util.UUID hostileTokenId) {
    public MovementResolutionResult {
        traversedPath = List.copyOf(traversedPath);
        publicEvents = List.copyOf(publicEvents);
        status = status == null
                ? interruptionReason == null ? MovementResolutionOutcomeStatus.COMMITTED : MovementResolutionOutcomeStatus.INTERRUPTED
                : status;
    }

    public MovementResolutionResult(MovementPath requestedPath, List<GridPosition> traversedPath,
            GridPosition finalPosition, long mapVersion, List<String> publicEvents, String interruptionReason) {
        this(requestedPath, traversedPath, finalPosition, mapVersion, publicEvents, interruptionReason, null, null);
    }

    public MovementResolutionResult(MovementPath requestedPath, List<GridPosition> traversedPath,
            GridPosition finalPosition, long mapVersion, List<String> publicEvents, String interruptionReason,
            MovementResolutionOutcomeStatus status) {
        this(requestedPath, traversedPath, finalPosition, mapVersion, publicEvents, interruptionReason, status, null);
    }
}
