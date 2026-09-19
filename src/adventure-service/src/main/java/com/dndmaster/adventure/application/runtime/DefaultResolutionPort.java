package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.FixedSaveDc;

public final class DefaultResolutionPort implements ResolutionPort {
    @Override
    public ResolutionResult resolve(CheckSelection selection, int systemRoll) {
        if (selection == null || selection.decision() == CheckSelection.Decision.NO_CHECK || selection.unit() == null) {
            throw new IllegalArgumentException("a selected check is required");
        }
        if (systemRoll < 1 || systemRoll > 20) throw new IllegalArgumentException("system roll must be between 1 and 20");
        if (!(selection.unit().dc() instanceof FixedSaveDc fixed)) {
            throw new IllegalArgumentException("system check requires a fixed DC");
        }
        return new ResolutionResult(selection.unit(), systemRoll, systemRoll >= fixed.value());
    }

    @Override
    public PlayerCheckResult resolvePlayerCheck(PlayerCheckRequest request) {
        if (request == null || request.ruleReference() == null || request.ruleReference().isBlank()) {
            throw new IllegalArgumentException("a player check rule reference is required");
        }
        if (request.difficulty() < 0) throw new IllegalArgumentException("player check difficulty must not be negative");
        if (request.rollTotal() < 1 || request.rollTotal() > 20) {
            throw new IllegalArgumentException("player check roll must be between 1 and 20");
        }
        return new PlayerCheckResult(request.rollTotal(), request.rollTotal() >= request.difficulty());
    }
}
