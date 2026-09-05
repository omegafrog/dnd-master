package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.UUID;

/** Typed AI interpretation. It carries costs and effects, not executable provider JSON. */
public record FreeFormActionPlan(UUID actorId, UUID targetId, TurnResourceCost cost, boolean requiresRoll,
                                 Integer targetArmorClass, Integer attackModifier, CombatEffectProposal effects,
                                 String judgment, String narration) {
    public FreeFormActionPlan {
        Objects.requireNonNull(actorId, "actor id must not be null");
        Objects.requireNonNull(cost, "free-form cost must not be null");
        Objects.requireNonNull(effects, "free-form effects must not be null");
        if (judgment == null || judgment.isBlank()) throw new IllegalArgumentException("free-form judgment must not be blank");
        if (narration == null || narration.isBlank()) throw new IllegalArgumentException("free-form narration must not be blank");
        judgment = judgment.trim();
        narration = narration.trim();
    }

    public static FreeFormActionPlan narrativeOnly(UUID actorId, TurnResourceCost cost,
                                                    String judgment, String narration) {
        return new FreeFormActionPlan(actorId, null, cost, false, null, null,
                CombatEffectProposal.none(), judgment, narration);
    }
}
