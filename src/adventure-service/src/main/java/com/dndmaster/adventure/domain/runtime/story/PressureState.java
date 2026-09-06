package com.dndmaster.adventure.domain.runtime.story;

import java.util.Objects;

public record PressureState(String pressureId, PressureStatus status, int progression, String material) {
    public PressureState {
        if (pressureId == null || pressureId.isBlank()) throw new IllegalArgumentException("pressure id is required");
        status = Objects.requireNonNull(status, "pressure status must not be null");
        if (progression < 0) throw new IllegalArgumentException("pressure progression must not be negative");
        material = material == null || material.isBlank() ? null : material.trim();
        pressureId = pressureId.trim();
    }

    public static PressureState dormant(String pressureId) {
        return new PressureState(pressureId, PressureStatus.DORMANT, 0, null);
    }

    public PressureState advance() {
        return new PressureState(pressureId, PressureStatus.ACTIVE, progression + 1, material);
    }

    public PressureState skip() {
        return new PressureState(pressureId, PressureStatus.SKIPPED, progression, material);
    }

    public PressureState cancel() {
        return new PressureState(pressureId, PressureStatus.CANCELLED, progression, material);
    }

    public PressureState replace(String replacement) {
        return new PressureState(pressureId, PressureStatus.REPLACED, progression + 1, replacement);
    }
}
