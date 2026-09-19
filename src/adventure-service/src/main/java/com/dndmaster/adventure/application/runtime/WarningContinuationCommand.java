package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

public record WarningContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
        UUID hostileTokenId, String trigger) implements RuntimeContinuationCommandOutcome { }
