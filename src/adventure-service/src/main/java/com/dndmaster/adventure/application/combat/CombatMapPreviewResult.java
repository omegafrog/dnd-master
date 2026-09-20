package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CombatMapPreviewResult(UUID mapId, List<CombatMapPreviewPosition> orderedPositions,
        int distance, long baseMapVersion, String fingerprint) {
    public CombatMapPreviewResult {
        Objects.requireNonNull(mapId, "map id must not be null");
        orderedPositions = List.copyOf(Objects.requireNonNull(orderedPositions, "preview path must not be null"));
        if (orderedPositions.isEmpty()) throw new IllegalArgumentException("preview path must not be empty");
        if (distance < 0 || baseMapVersion < 0) throw new IllegalArgumentException("preview values must not be negative");
        if (fingerprint == null || fingerprint.isBlank()) throw new IllegalArgumentException("preview fingerprint must not be blank");
    }
}
