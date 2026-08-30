package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Phase/count progress; percentage is derived only when a real total is known. */
public record PreparationProgress(String phase, int completedUnits, Integer totalUnits) {
    public PreparationProgress {
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("progress phase must not be blank");
        phase = phase.trim();
        if (completedUnits < 0) throw new IllegalArgumentException("completed units must not be negative");
        if (totalUnits != null && totalUnits <= 0) throw new IllegalArgumentException("total units must be positive when supplied");
        if (totalUnits != null && completedUnits > totalUnits) throw new IllegalArgumentException("completed units exceed total units");
    }

    public static PreparationProgress of(String phase, int completedUnits, Integer totalUnits) {
        return new PreparationProgress(phase, completedUnits, totalUnits);
    }

    public static PreparationProgress legacy(int percentage) {
        if (percentage < 0 || percentage > 100) throw new IllegalArgumentException("legacy progress must be 0..100");
        return new PreparationProgress("LEGACY", percentage, 100);
    }

    public Integer percentage() {
        return totalUnits == null ? null : (int) Math.round(completedUnits * 100.0 / totalUnits);
    }
}
