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
        if (request == null) throw new IllegalArgumentException("player check request is required");
        TypedCheckRule rule = request.rule();
        return new PlayerCheckResult(rule.ruleReference(), request.diceExpression(), rule.modifier(),
                rule.difficulty(), request.rollTotal(), rule.accepts(request.rollTotal()));
    }
    @Override
    public EnemyObservationCheckResult resolveEnemyObservation(EnemyObservationCheckRequest request) {
        if (request == null) throw new IllegalArgumentException("enemy observation check request is required");
        TypedCheckRule rule = request.rule();
        return new EnemyObservationCheckResult(rule.ruleReference(), request.diceExpression(), rule.modifier(),
                rule.difficulty(), request.rollTotal(), rule.accepts(request.rollTotal()));
    }
}
