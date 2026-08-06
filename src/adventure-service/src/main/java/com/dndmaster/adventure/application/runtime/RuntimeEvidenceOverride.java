package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Evaluation-only evidence seam. Normal player commands leave this empty. */
public record RuntimeEvidenceOverride(String condition, EvidencePack evidencePack) {
    public RuntimeEvidenceOverride {
        if (condition == null || condition.isBlank()) throw new IllegalArgumentException("evidence condition required");
        condition = condition.trim().toUpperCase(java.util.Locale.ROOT);
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack required");
    }
}
