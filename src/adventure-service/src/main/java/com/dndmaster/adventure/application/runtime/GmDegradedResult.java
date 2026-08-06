package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Persistable safety metadata carried in RuntimePlan warnings. */
public record GmDegradedResult(GmDegradedMode mode, String reason, boolean repairAttempted) {
    public GmDegradedResult {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        reason = reason.trim();
    }

    public String warning() {
        return "degraded-mode:" + mode + ";repair-attempted=" + repairAttempted + ";refusal-reason=" + reason;
    }
}
