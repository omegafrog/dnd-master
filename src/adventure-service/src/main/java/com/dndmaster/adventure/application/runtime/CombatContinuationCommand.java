package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

public record CombatContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
        UUID hostileTokenId, String trigger) implements RuntimeContinuationCommandOutcome { }
