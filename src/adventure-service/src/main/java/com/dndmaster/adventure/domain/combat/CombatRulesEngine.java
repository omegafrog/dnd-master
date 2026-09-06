package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

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

    /** Same deterministic validation for an AI actor; human commands remain player-only. */
    public CombatActionEvaluation validateAiAction(CombatEncounter encounter, CombatActionIntent intent) {
        Objects.requireNonNull(encounter, "encounter must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        if (encounter.status() != CombatEncounter.Status.ACTIVE) {
            return CombatActionEvaluation.rejected("COMBAT_NOT_ACTIVE");
        }
        if (!encounter.currentParticipantId().equals(intent.actorId())) {
            return CombatActionEvaluation.rejected("NOT_CURRENT_ACTOR");
        }
        if (encounter.currentParticipant().controller() != CombatParticipant.Controller.AI) {
            return CombatActionEvaluation.rejected("NOT_AI_TURN");
        }
        return CombatActionEvaluation.accepted(intent.cost());
    }

    /** Validates the complete AI interpretation before any reservation or external effect. */
    public CombatActionEvaluation validateFreeFormProposal(CombatEncounter encounter, FreeFormActionPlan proposal) {
        Objects.requireNonNull(encounter, "encounter must not be null");
        Objects.requireNonNull(proposal, "free-form proposal must not be null");
        CombatActionEvaluation state = validateAction(encounter,
                new CombatActionIntent(proposal.actorId(), "FREE_FORM", proposal.cost()));
        if (!state.accepted()) return state;

        List<String> violations = new ArrayList<>();
        if (proposal.targetId() != null && !hasParticipant(encounter, proposal.targetId())) {
            violations.add("TARGET_NOT_IN_ENCOUNTER");
        }
        if (proposal.effects().hasCharacterEffects() && proposal.targetId() == null) {
            violations.add("TARGET_REQUIRED_FOR_EFFECT");
        }
        Integer targetArmorClass = proposal.targetArmorClass();
        if (targetArmorClass == null && proposal.targetId() != null) {
            targetArmorClass = encounter.participants().stream()
                    .filter(participant -> participant.participantId().equals(proposal.targetId()))
                    .map(CombatParticipant::statBlock)
                    .filter(java.util.Objects::nonNull)
                    .map(CombatEnemyStatBlock::armorClass)
                    .findFirst().orElse(null);
        }
        Integer attackModifier = proposal.attackModifier();
        if (attackModifier == null && encounter.currentParticipant().statBlock() != null) {
            attackModifier = encounter.currentParticipant().statBlock().attackModifier();
        }
        if (proposal.requiresRoll() && (targetArmorClass == null || attackModifier == null)) {
            violations.add("ROLL_REQUIRED_FOR_ATTACK");
        }
        if ((targetArmorClass == null) != (attackModifier == null)) {
            violations.add("ROLL_CONFIGURATION_INCOMPLETE");
        }
        if (proposal.effects().mapEffect() != null
                && !proposal.effects().mapEffect().tokenId().equals(proposal.actorId())) {
            violations.add("MAP_EFFECT_ACTOR_MISMATCH");
        }
        try {
            encounter.currentParticipant().resources().reserve(proposal.cost());
        } catch (RuntimeException exception) {
            violations.add("INSUFFICIENT_RESOURCE");
        }
        return violations.isEmpty() ? CombatActionEvaluation.accepted(proposal.cost())
                : CombatActionEvaluation.rejected(violations);
    }

    private static boolean hasParticipant(CombatEncounter encounter, java.util.UUID participantId) {
        return encounter.participants().stream().anyMatch(p -> p.participantId().equals(participantId));
    }
}
