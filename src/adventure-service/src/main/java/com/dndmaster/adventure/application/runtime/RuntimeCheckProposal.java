package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A GM-proposed check for this player action, grounded in the selected turn evidence. */
public record RuntimeCheckProposal(
        boolean required,
        String reason,
        String abilityOrSkill,
        UUID characterSheetId,
        RollMethod rollMethod,
        String diceExpression,
        int modifier,
        Integer difficulty,
        List<String> evidenceKeys,
        String successOutcome,
        String failureOutcome) {
    public RuntimeCheckProposal {
        reason = reason == null ? "" : reason.trim();
        abilityOrSkill = abilityOrSkill == null ? "" : abilityOrSkill.trim();
        diceExpression = diceExpression == null ? "" : diceExpression.trim();
        evidenceKeys = List.copyOf(Objects.requireNonNullElse(evidenceKeys, List.of()));
        successOutcome = successOutcome == null ? "" : successOutcome.trim();
        failureOutcome = failureOutcome == null ? "" : failureOutcome.trim();
        if (!required) {
            if (characterSheetId != null || rollMethod != null || !abilityOrSkill.isEmpty()
                    || !diceExpression.isEmpty() || difficulty != null || !evidenceKeys.isEmpty()
                    || !successOutcome.isEmpty() || !failureOutcome.isEmpty()) {
                throw new IllegalArgumentException("a no-check proposal cannot contain roll details");
            }
        } else {
            if (reason.isBlank() || abilityOrSkill.isBlank() || characterSheetId == null || rollMethod == null
                    || diceExpression.isBlank() || difficulty == null || evidenceKeys.isEmpty()) {
                throw new IllegalArgumentException("a required check must include its target, rule, and evidence");
            }
            if (modifier < -10 || modifier > 20) throw new IllegalArgumentException("check modifier is outside the supported range");
            if (difficulty < 1 || difficulty > 40) throw new IllegalArgumentException("check difficulty is outside the supported range");
            if (successOutcome.isBlank() || failureOutcome.isBlank()) {
                throw new IllegalArgumentException("a required check must define success and failure outcomes");
            }
            TypedCheckRule normalizedRule = new TypedCheckRule(evidenceKeys.getFirst(), diceExpression, modifier, difficulty);
            diceExpression = normalizedRule.diceExpression();
            modifier = normalizedRule.modifier();
        }
    }

    public static RuntimeCheckProposal none() {
        return new RuntimeCheckProposal(false, "", "", null, null, "", 0, null, List.of(), "", "");
    }

    public enum RollMethod { PLAYER, SYSTEM }
}
