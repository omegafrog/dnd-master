package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.Optional;
import java.util.List;

public interface AdventureStoryPlanRepository {
    Optional<AdventureStoryPlan> findBySessionId(SessionId sessionId);
    void save(AdventureStoryPlan plan);
    default List<AdventureStoryPlan> readHistory(SessionId sessionId) { return findBySessionId(sessionId).stream().toList(); }
}
