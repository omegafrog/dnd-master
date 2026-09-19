package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CombatMapSpatialResult(UUID mapId, long version, List<String> publicEvents, UUID operationId,
        String status, CombatMapPendingCheck pendingCheck) {
    public CombatMapSpatialResult(UUID mapId, long version, List<String> publicEvents) {
        this(mapId, version, publicEvents, null, null, null);
    }
    public CombatMapSpatialResult {
        Objects.requireNonNull(mapId);
        if (version < 0) throw new IllegalArgumentException("map version must be non-negative");
        publicEvents = publicEvents == null ? List.of() : List.copyOf(publicEvents);
    }
}
