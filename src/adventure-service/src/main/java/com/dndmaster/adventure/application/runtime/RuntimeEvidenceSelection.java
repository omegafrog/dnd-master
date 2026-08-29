package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record RuntimeEvidenceSelection(EvidencePack pack, RuntimeEvidenceSelectionMetrics metrics) {
    public RuntimeEvidenceSelection {
        pack = Objects.requireNonNull(pack, "evidence pack must not be null");
        metrics = Objects.requireNonNull(metrics, "evidence metrics must not be null");
        if (metrics.selectedCount() != pack.totalEvidenceCount()) {
            throw new IllegalArgumentException("evidence metrics do not match pack");
        }
    }
}
