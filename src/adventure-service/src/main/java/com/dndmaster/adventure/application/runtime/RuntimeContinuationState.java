package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import java.util.Objects;
import java.util.UUID;

/** Typed record of the Runtime transition requested by a movement follow-up. */
public record RuntimeContinuationState(UUID commandId, UUID turnId, UUID operationId, UUID hostileTokenId,
        MovementFollowUpCommand.Kind kind, Status status) {
    public RuntimeContinuationState(UUID commandId, UUID turnId, UUID operationId,
            MovementFollowUpCommand.Kind kind, Status status) {
        this(commandId, turnId, operationId, null, kind, status);
    }
    public enum Status { APPLIED }

    public RuntimeContinuationState {
        Objects.requireNonNull(commandId, "continuation command id must not be null");
        Objects.requireNonNull(turnId, "continuation turn id must not be null");
        Objects.requireNonNull(operationId, "continuation operation id must not be null");
        Objects.requireNonNull(kind, "continuation kind must not be null");
        Objects.requireNonNull(status, "continuation status must not be null");
    }
}
