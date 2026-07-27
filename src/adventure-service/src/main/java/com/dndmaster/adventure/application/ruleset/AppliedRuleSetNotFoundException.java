package com.dndmaster.adventure.application.ruleset;

public final class AppliedRuleSetNotFoundException extends RuntimeException {
    public AppliedRuleSetNotFoundException() {
        super("applied rule set was not found");
    }
}
