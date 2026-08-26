package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.Optional;
import java.util.List;

public interface AdventureStoryPlanRepository {
    Optional<AdventureStoryPlan> findBySessionId(SessionId sessionId);
    void save(AdventureStoryPlan plan);
    default void save(AdventureStoryPlan plan, String cause) { save(plan); }
    default List<AdventureStoryPlan> readHistory(SessionId sessionId) { return findBySessionId(sessionId).stream().toList(); }
    default List<AdventureStoryPlanHistoryEntry> readHistoryEntries(SessionId sessionId) {
        return readHistory(sessionId).stream().map(plan -> new AdventureStoryPlanHistoryEntry(
                plan, plan.planId(), plan.updatedAt(), "LEGACY", null)).toList();
    }
}
