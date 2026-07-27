package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record PlayabilityReport(
        PlayabilityStatus status,
        List<String> warnings,
        List<String> blockers,
        List<String> limits,
        List<InitialSourceContextCandidate> candidates) {
    public PlayabilityReport {
        status = Objects.requireNonNull(status, "status must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers must not be null"));
        limits = List.copyOf(Objects.requireNonNull(limits, "limits must not be null"));
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
    }

    public boolean isBlocked() {
        return status == PlayabilityStatus.BLOCKED;
    }
}
