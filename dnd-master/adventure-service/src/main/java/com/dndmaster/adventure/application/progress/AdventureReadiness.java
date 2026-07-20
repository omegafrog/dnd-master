package com.dndmaster.adventure.application.progress;

public record AdventureReadiness(boolean scenarioPrepared, boolean ruleSetPrepared, boolean characterPrepared) {
    public boolean ready() {
        return scenarioPrepared && ruleSetPrepared && characterPrepared;
    }
}
