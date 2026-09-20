package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Typed anti-corruption boundary for one authoritative Combat Map move. */
public record CombatMapMoveCommand(CombatActionCommand action, int distance, long expectedVersion, String appliedEdition,
        String previewFingerprint, java.util.List<CombatMapPreviewPosition> waypoints) {
    public CombatMapMoveCommand {
        Objects.requireNonNull(action, "map movement command must not be null");
        if (action.combatMapId() == null) throw new IllegalArgumentException("mapped movement requires map id");
        if (distance <= 0) throw new IllegalArgumentException("movement distance must be positive");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
        if (appliedEdition != null && appliedEdition.isBlank()) throw new IllegalArgumentException("applied edition must not be blank");
        if (previewFingerprint != null && previewFingerprint.isBlank()) throw new IllegalArgumentException("preview fingerprint must not be blank");
        java.util.List<CombatMapPreviewPosition> suppliedWaypoints = waypoints == null ? java.util.List.of() : waypoints;
        if (suppliedWaypoints.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("waypoints must not contain null");
        waypoints = java.util.List.copyOf(suppliedWaypoints);
    }

    public CombatMapMoveCommand(CombatActionCommand action, int distance) {
        this(action, distance, action.mapVersion() == null ? action.expectedVersion() : action.mapVersion(), null, null, java.util.List.of());
    }

    public CombatMapMoveCommand(CombatActionCommand action, int distance, long expectedVersion) {
        this(action, distance, expectedVersion, null, null, java.util.List.of());
    }

    public CombatMapMoveCommand(CombatActionCommand action, int distance, long expectedVersion, String appliedEdition) {
        this(action, distance, expectedVersion, appliedEdition, null, java.util.List.of());
    }
}
