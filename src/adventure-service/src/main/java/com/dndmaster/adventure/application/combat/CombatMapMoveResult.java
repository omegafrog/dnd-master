package com.dndmaster.adventure.application.combat;

public record CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
        java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
        java.util.List<String> publicEvents, String interruptionReason, CombatMapPendingCheck pendingCheck) {
    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> requestedPath, java.util.List<CombatMapPreviewPosition> traversedPath,
            CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents, String interruptionReason) {
        this(version, operationId, status, requestedPath, traversedPath, finalPosition, publicEvents, interruptionReason, null);
    }
    public CombatMapMoveResult {
        if (version < 0) throw new IllegalArgumentException("map version must be non-negative");
        traversedPath = traversedPath == null ? java.util.List.of() : java.util.List.copyOf(traversedPath);
        publicEvents = publicEvents == null ? java.util.List.of() : java.util.List.copyOf(publicEvents);
        requestedPath = requestedPath == null ? java.util.List.of() : java.util.List.copyOf(requestedPath);
    }
    public CombatMapMoveResult(long version) { this(version, null, CombatMapMovementStatus.COMMITTED, java.util.List.of(), java.util.List.of(), null, java.util.List.of(), null); }

    public CombatMapMoveResult(long version, java.util.UUID operationId, CombatMapMovementStatus status,
            java.util.List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
            java.util.List<String> publicEvents, String interruptionReason) {
        this(version, operationId, status, java.util.List.of(), traversedPath, finalPosition, publicEvents, interruptionReason, null);
    }
}
