package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import java.util.UUID;

public record ChaseContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
        UUID hostileTokenId, String trigger, MovementFollowUpCommand.Kind kind)
        implements RuntimeContinuationCommandOutcome {
    public ChaseContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
            UUID hostileTokenId, String trigger) {
        this(commandId, turnId, operationId, hostileTokenId, trigger, MovementFollowUpCommand.Kind.CHASE);
    }
}
