package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryPlanRevisionRepository {
    Optional<AdventureStoryPlanRevision> current(UUID sessionId);
    List<AdventureStoryPlanRevision> history(UUID sessionId);
    void append(AdventureStoryPlanRevision revision);
}
