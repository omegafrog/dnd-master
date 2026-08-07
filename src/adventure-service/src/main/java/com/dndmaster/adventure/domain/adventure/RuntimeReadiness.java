package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record RuntimeReadiness(long bindingVersion, RuntimeReadinessStatus status,
                               List<String> blockers, List<String> warnings, boolean retryable) {
    public RuntimeReadiness {
        if (bindingVersion <= 0) throw new IllegalArgumentException("binding version must be positive");
        status = Objects.requireNonNull(status, "status must not be null");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }

    public boolean ready() {
        return status == RuntimeReadinessStatus.INDEXED_READY
                || status == RuntimeReadinessStatus.SUPPORTED_DEGRADED;
    }
}
