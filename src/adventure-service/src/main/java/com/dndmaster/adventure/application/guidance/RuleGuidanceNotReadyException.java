package com.dndmaster.adventure.application.guidance;

public final class RuleGuidanceNotReadyException extends RuntimeException {
    public RuleGuidanceNotReadyException() { super("rule set and indexes must be ready"); }
}
