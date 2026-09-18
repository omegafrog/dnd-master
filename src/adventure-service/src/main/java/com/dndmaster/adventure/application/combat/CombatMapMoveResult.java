package com.dndmaster.adventure.application.combat;

public record CombatMapMoveResult(long version, java.util.UUID operationId, String status,
        java.util.List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
        java.util.List<String> publicEvents, String interruptionReason) {
    public CombatMapMoveResult {
        if (version < 0) throw new IllegalArgumentException("map version must be non-negative");
        traversedPath = traversedPath == null ? java.util.List.of() : java.util.List.copyOf(traversedPath);
        publicEvents = publicEvents == null ? java.util.List.of() : java.util.List.copyOf(publicEvents);
    }
    public CombatMapMoveResult(long version) { this(version, null, "COMMITTED", java.util.List.of(), null, java.util.List.of(), null); }
}
