package com.dndmaster.adventure.domain.runtime.narrative;

public record RecentEvent(String id, long turn, String summary) {
    public RecentEvent {
        if (id == null || id.isBlank() || summary == null || summary.isBlank()) throw new IllegalArgumentException("recent event fields must not be blank");
        if (turn < 0) throw new IllegalArgumentException("event turn must not be negative");
        id = id.trim(); summary = summary.trim();
    }
}
