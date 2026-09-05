package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.ReactionChoice;
import java.util.UUID;

public record ReactionResolutionResponse(UUID encounterId, UUID reactionId, UUID operationId,
                                         String resumeStep, ReactionChoice choice,
                                         long encounterVersion, String status) {}
