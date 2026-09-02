package com.dndmaster.adventure.application.runtime;

public record TriggerInput(String action, boolean worldEvent) {
    public TriggerInput {
        action = action == null ? "" : action.trim();
    }
}
