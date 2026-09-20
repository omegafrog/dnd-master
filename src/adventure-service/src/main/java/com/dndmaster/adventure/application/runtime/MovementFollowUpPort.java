package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;

import java.util.Objects;
import java.util.UUID;

/** Runtime-owned boundary for publishing the next adventure progression. */
@FunctionalInterface
public interface MovementFollowUpPort {
    Result publish(MovementFollowUpCommand command, UUID adventureId, UUID sessionId, UUID ownerPlayerId);

    record Result(Status status, String value) {
        public enum Status { DONE, RETRY, PERMANENT_FAILURE }
        public Result {
            status = Objects.requireNonNull(status, "follow-up status must not be null");
            value = value == null ? "" : value;
        }
        public static Result done(String value) { return new Result(Status.DONE, value); }
        public static Result retry(String value) { return new Result(Status.RETRY, value); }
        public static Result permanentFailure(String value) { return new Result(Status.PERMANENT_FAILURE, value); }
    }
}
