package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;

/** ACL for typed AI decisions; provider-specific response shapes stop here. */
@FunctionalInterface
public interface AiCombatDecisionPort {
    FreeFormActionPlan interpretFreeForm(FreeFormCombatContext context);

    default AiTurnPlan planTurn(AiCombatTurnContext context) {
        throw new UnsupportedOperationException("AI combat turn decisions are not configured");
    }
}
