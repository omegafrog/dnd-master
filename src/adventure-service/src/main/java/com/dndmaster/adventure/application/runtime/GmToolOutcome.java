package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record GmToolOutcome(Status status, String value) {
    public enum Status { COMPLETED, REJECTED, REQUIRES_CHOICE, UNKNOWN }
    public GmToolOutcome { Objects.requireNonNull(status); value = value == null ? "" : value; }
    public static GmToolOutcome completed(String value) { return new GmToolOutcome(Status.COMPLETED, value); }
    public static GmToolOutcome rejected(String value) { return new GmToolOutcome(Status.REJECTED, value); }
    public static GmToolOutcome requiresChoice(String value) { return new GmToolOutcome(Status.REQUIRES_CHOICE, value); }
    public static GmToolOutcome unknown(String value) { return new GmToolOutcome(Status.UNKNOWN, value); }
}
