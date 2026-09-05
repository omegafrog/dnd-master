package com.dndmaster.adventure.domain.combat;

import java.util.Objects;

/** Deterministic combat validation; it never calls an external service or mutates state. */
public final class CombatRulesEngine {
    public CombatActionEvaluation validateAction(CombatEncounter encounter, CombatActionIntent intent) {
        Objects.requireNonNull(encounter, "encounter must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        if (encounter.status() != CombatEncounter.Status.ACTIVE) {
            return CombatActionEvaluation.rejected("COMBAT_NOT_ACTIVE");
        }
        if (!encounter.currentParticipantId().equals(intent.actorId())) {
            return CombatActionEvaluation.rejected("NOT_CURRENT_ACTOR");
        }
        CombatParticipant actor = encounter.currentParticipant();
        if (actor.controller() != CombatParticipant.Controller.PLAYER) {
            return CombatActionEvaluation.rejected("NOT_PLAYER_TURN");
        }
        return CombatActionEvaluation.accepted(intent.cost());
    }
}
