package com.dndmaster.adventure.application.runtime;

public record RuntimeCommandOutcome(RuntimeCommandStatus status, String value, long version, String continuityReference) {
    public RuntimeCommandOutcome(RuntimeCommandStatus status, String value, long version) { this(status, value, version, ""); }
    public static RuntimeCommandOutcome applied(String value, long version) { return new RuntimeCommandOutcome(RuntimeCommandStatus.APPLIED, value, version); }
    public static RuntimeCommandOutcome applied(String value, long version, String reference) { return new RuntimeCommandOutcome(RuntimeCommandStatus.APPLIED, value, version, reference); }
    public static RuntimeCommandOutcome rejected(String value) { return new RuntimeCommandOutcome(RuntimeCommandStatus.REJECTED, value, 0); }
    public static RuntimeCommandOutcome unknown(String value) { return new RuntimeCommandOutcome(RuntimeCommandStatus.UNKNOWN, value, 0); }
}
