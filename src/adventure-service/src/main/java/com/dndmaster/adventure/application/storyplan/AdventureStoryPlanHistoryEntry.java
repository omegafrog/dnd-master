package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import java.time.Instant;
import java.util.UUID;

public record AdventureStoryPlanHistoryEntry(
        AdventureStoryPlan plan,
        UUID historyId,
        Instant recordedAt,
        String cause,
        UUID predecessorHistoryId) {
    public AdventureStoryPlanHistoryEntry {
        if (plan == null || historyId == null || recordedAt == null || cause == null || cause.isBlank()) {
            throw new IllegalArgumentException("complete story-plan history metadata is required");
        }
        cause = cause.trim();
    }
}
