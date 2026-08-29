package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.Set;

public record ActiveThread(String id, String summary, String status, Set<String> factIds) {
    public ActiveThread {
        if (id == null || id.isBlank() || summary == null || summary.isBlank() || status == null || status.isBlank())
            throw new IllegalArgumentException("active thread fields must not be blank");
        id = id.trim(); summary = summary.trim(); status = status.trim(); factIds = Set.copyOf(factIds == null ? Set.of() : factIds);
    }
}
