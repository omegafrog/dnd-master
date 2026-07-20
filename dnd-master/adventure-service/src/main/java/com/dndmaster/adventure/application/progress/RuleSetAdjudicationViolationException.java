package com.dndmaster.adventure.application.progress;

public final class RuleSetAdjudicationViolationException extends RuntimeException {
    public RuleSetAdjudicationViolationException() {
        super("AI adjudication is outside the selected rule set");
    }
}
