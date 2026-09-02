package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

public final class GmTurnAlreadyInProgressException extends RuntimeException {
    public GmTurnAlreadyInProgressException(UUID sessionId, UUID turnId) {
        super("GM_TURN_ALREADY_IN_PROGRESS session=" + sessionId + " turn=" + turnId);
    }
}
