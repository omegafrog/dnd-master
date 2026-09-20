package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.List;
import java.util.Objects;

/** 저장하지 않으며 플레이어에게 전달할 수 있는 이동 미리보기다. */
public record MovementPreview(
        List<GridPosition> orderedPositions,
        int distance,
        long baseMapVersion,
        String fingerprint) {
    public MovementPreview {
        orderedPositions = List.copyOf(Objects.requireNonNull(orderedPositions, "preview path must not be null"));
        if (orderedPositions.isEmpty() || orderedPositions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("preview path must contain positions");
        }
        if (distance < 0) throw new IllegalArgumentException("preview distance must not be negative");
        if (baseMapVersion < 0) throw new IllegalArgumentException("preview map version must not be negative");
        fingerprint = Objects.requireNonNull(fingerprint, "preview fingerprint must not be null");
        if (fingerprint.isBlank()) throw new IllegalArgumentException("preview fingerprint must not be blank");
    }
}
