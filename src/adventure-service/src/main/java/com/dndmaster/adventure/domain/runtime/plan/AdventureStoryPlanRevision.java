package com.dndmaster.adventure.domain.runtime.plan;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AdventureStoryPlanRevision(UUID revisionId, UUID sessionId, long version,
                                         UUID predecessorRevisionId, UUID causeTurnId, List<String> stages) {
    public AdventureStoryPlanRevision {
        Objects.requireNonNull(revisionId); Objects.requireNonNull(sessionId); Objects.requireNonNull(causeTurnId);
        if (version < 1 || stages == null || stages.isEmpty()) throw new IllegalArgumentException("invalid story plan revision");
        stages = stages.stream().map(stage -> { if (stage == null || stage.isBlank()) throw new IllegalArgumentException("stage must not be blank"); return stage.trim(); }).toList();
    }
    public static AdventureStoryPlanRevision initial(UUID sessionId, List<String> stages, UUID causeTurnId) { return new AdventureStoryPlanRevision(UUID.randomUUID(), sessionId, 1, null, causeTurnId, stages); }
}
