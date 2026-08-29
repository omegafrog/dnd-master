package com.dndmaster.gmeval.registry;

public final class StalePromptActivationException extends IllegalStateException {
    private final PromptRole role;
    private final PromptVersion expected;
    private final PromptVersion actual;

    public StalePromptActivationException(PromptRole role, PromptVersion expected, PromptVersion actual) {
        super("stale active prompt for " + role + ": expected " + expected + ", actual " + actual);
        this.role = role;
        this.expected = expected;
        this.actual = actual;
    }

    public PromptRole role() { return role; }
    public PromptVersion expected() { return expected; }
    public PromptVersion actual() { return actual; }
}
