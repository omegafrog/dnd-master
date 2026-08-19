package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** Runtime seam used after an authored trigger has been evaluated. */
public interface TacticalTriggerRuntimePort {
    void apply(UUID combatMapId, UUID ownerPlayerId, long expectedVersion, UUID commandId,
            TacticalTriggerEvaluator.Evaluation evaluation);
}
