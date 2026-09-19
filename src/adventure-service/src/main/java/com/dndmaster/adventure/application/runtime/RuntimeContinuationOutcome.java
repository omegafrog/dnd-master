package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record RuntimeContinuationOutcome(Status status, String value) {
    public enum Status { APPLIED, RETRY }

    public RuntimeContinuationOutcome {
        status = Objects.requireNonNull(status, "continuation outcome status must not be null");
        value = value == null ? "" : value;
    }

    public static RuntimeContinuationOutcome applied(String value) {
        return new RuntimeContinuationOutcome(Status.APPLIED, value);
    }

    public static RuntimeContinuationOutcome retry(String value) {
        return new RuntimeContinuationOutcome(Status.RETRY, value);
    }
}
