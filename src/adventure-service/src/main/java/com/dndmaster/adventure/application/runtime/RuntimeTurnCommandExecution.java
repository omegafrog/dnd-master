package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.CombatMapMoveResult;

/** Adapter result; transient failures remain resumable, permanent failures require repair. */
public record RuntimeTurnCommandExecution(Status status, String value, CombatMapMoveResult movementResult,
        MovementConflict movementConflict) {
    public enum Status { DONE, TRANSIENT_FAILURE, PERMANENT_FAILURE }
    public record MovementConflict(int httpStatus, String code) { }

    public RuntimeTurnCommandExecution {
        if (status == null) throw new NullPointerException("command execution status must not be null");
        value = value == null ? "" : value;
    }

    public RuntimeTurnCommandExecution(Status status, String value) {
        this(status, value, null, null);
    }

    public static RuntimeTurnCommandExecution done(String value) {
        return new RuntimeTurnCommandExecution(Status.DONE, value);
    }

    public static RuntimeTurnCommandExecution transientFailure(String value) {
        return new RuntimeTurnCommandExecution(Status.TRANSIENT_FAILURE, value);
    }

    public static RuntimeTurnCommandExecution permanentFailure(String value) {
        return new RuntimeTurnCommandExecution(Status.PERMANENT_FAILURE, value);
    }

    public static RuntimeTurnCommandExecution movement(Status status, String value, CombatMapMoveResult result) {
        return new RuntimeTurnCommandExecution(status, value, result, null);
    }

    public static RuntimeTurnCommandExecution movementConflict(int httpStatus, String code) {
        return new RuntimeTurnCommandExecution(Status.PERMANENT_FAILURE, code, null,
                new MovementConflict(httpStatus, code));
    }
}
