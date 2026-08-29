package com.dndmaster.adventure.application.runtime;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record GmValidationReport(
        List<GmValidationViolation> violations,
        int evidenceCount,
        int claimSupportCount,
        Map<RuntimeEvidenceType, Integer> evidenceByType) {
    public GmValidationReport {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
        evidenceByType = Map.copyOf(new EnumMap<>(Objects.requireNonNull(evidenceByType, "evidence metrics must not be null")));
        if (evidenceCount < 0 || claimSupportCount < 0) throw new IllegalArgumentException("validation metrics must not be negative");
    }

    public boolean passed() {
        return violations.isEmpty();
    }

    public double citationSupportRatio() {
        return evidenceCount == 0 ? 1d : claimSupportCount / (double) evidenceCount;
    }
}
