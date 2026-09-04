package com.dndmaster.adventure.application.runtime;

/** Adapter result; transient failures remain resumable, permanent failures require repair. */
public record RuntimeTurnCommandExecution(Status status, String value) {
    public enum Status { DONE, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    public RuntimeTurnCommandExecution {
        if (status == null) throw new NullPointerException("command execution status must not be null");
        value = value == null ? "" : value;
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
}
