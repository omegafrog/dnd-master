package com.dndmaster.adventure.application.guidance;

public final class OutOfScopeRuleEvidenceException extends RuntimeException {
    public OutOfScopeRuleEvidenceException() { super("rule evidence is outside selected rulebooks"); }
}
