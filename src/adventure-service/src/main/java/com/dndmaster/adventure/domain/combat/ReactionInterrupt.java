package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

/** Durable pause point for one operation; it contains no new roll or effect. */
public record ReactionInterrupt(UUID reactionId, String trigger, UUID eligibleActorId,
                                UUID suspendedOperationId, String resumeStep,
                                List<ReactionOption> options) {
    public ReactionInterrupt {
        if (reactionId == null || trigger == null || trigger.isBlank() || eligibleActorId == null
                || suspendedOperationId == null || resumeStep == null || resumeStep.isBlank()) {
            throw new IllegalArgumentException("reaction interrupt identity is required");
        }
        options = List.copyOf(options == null ? List.of() : options);
    }
}
