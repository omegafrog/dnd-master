package com.dndmaster.adventure.application.ability;

/** Signals that automatic preparation exhausted recovery and source based details are required. */
public final class AbilityDefinitionSourceInputRequiredException extends IllegalStateException {
    private final String abilityId;

    public AbilityDefinitionSourceInputRequiredException(String abilityId, Throwable cause) {
        super("ability definition requires source based input: " + abilityId, cause);
        this.abilityId = abilityId;
    }

    public String abilityId() { return abilityId; }
}
