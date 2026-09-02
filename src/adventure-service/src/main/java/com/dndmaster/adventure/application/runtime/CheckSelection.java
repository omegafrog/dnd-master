package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;

public record CheckSelection(Decision decision, String label, String diceExpression,
                             ScenarioResolutionUnit unit) {
    public enum Decision { NO_CHECK, SYSTEM_ROLL, PLAYER_ROLL }

    public CheckSelection {
        decision = decision == null ? Decision.NO_CHECK : decision;
        label = label == null ? "" : label.trim();
        diceExpression = diceExpression == null ? "" : diceExpression.trim();
    }

    public static CheckSelection noCheck() { return new CheckSelection(Decision.NO_CHECK, "", "", null); }

    public static CheckSelection from(TriggerDetection trigger) {
        if (trigger == null || !trigger.detected() || trigger.unit().check() == null) return noCheck();
        ScenarioResolutionUnit unit = trigger.unit();
        ScenarioResolutionDetail.RollMethod method = unit.check().rollMethod();
        Decision decision = method == ScenarioResolutionDetail.RollMethod.SYSTEM
                ? Decision.SYSTEM_ROLL : Decision.PLAYER_ROLL;
        return new CheckSelection(decision, unit.abilityOrSkill(), unit.diceExpression(), unit);
    }
}
