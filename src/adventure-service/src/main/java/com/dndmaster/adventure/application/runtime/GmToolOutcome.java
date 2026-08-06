package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record GmToolOutcome(Status status, String value, long version, String reference) {
    public enum Status { COMPLETED, REJECTED, REQUIRES_CHOICE, UNKNOWN }
    public GmToolOutcome(Status status, String value) { this(status, value, 0, ""); }
    public GmToolOutcome { Objects.requireNonNull(status); value = value == null ? "" : value; reference = reference == null ? "" : reference; if (version < 0) throw new IllegalArgumentException("tool version must not be negative"); }
    public static GmToolOutcome completed(String value) { return new GmToolOutcome(Status.COMPLETED, value); }
    public static GmToolOutcome completed(String value, long version, String reference) { return new GmToolOutcome(Status.COMPLETED, value, version, reference); }
    public static GmToolOutcome rejected(String value) { return new GmToolOutcome(Status.REJECTED, value); }
    public static GmToolOutcome requiresChoice(String value) { return new GmToolOutcome(Status.REQUIRES_CHOICE, value); }
    public static GmToolOutcome unknown(String value) { return new GmToolOutcome(Status.UNKNOWN, value); }
}
