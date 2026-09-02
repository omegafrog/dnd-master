package com.dndmaster.adventure.application.runtime;

public final class ToolAuthorizationException extends RuntimeException {
    private final ToolCapabilityDenialReason reason;
    private final String toolName;
    public ToolAuthorizationException() { this(ToolCapabilityDenialReason.EXPIRED, "<revoked>"); }
    public ToolAuthorizationException(ToolCapabilityDenialReason reason, String toolName) {
        super("tool capability denied: " + reason);
        this.reason = reason; this.toolName = toolName;
    }
    public ToolCapabilityDenialReason reason() { return reason; }
    public String toolName() { return toolName; }
}
