package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatActionIntent;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral AI proposal consumed by the normal combat action pipeline. */
public record AiTurnPlan(UUID actorId, CombatActionIntent intent, Integer targetArmorClass,
                         Integer attackModifier, UUID targetId, Integer damageAmount,
                         boolean endTurn, String narration) {
    public AiTurnPlan {
        Objects.requireNonNull(actorId, "AI actor must not be null");
        if (endTurn && intent != null) throw new IllegalArgumentException("end-turn plan cannot also contain an action");
        if (!endTurn && intent == null) throw new IllegalArgumentException("action plan must contain an intent");
        if (damageAmount != null && damageAmount < 1) throw new IllegalArgumentException("damage must be positive");
    }

    public AiTurnPlan(UUID actorId, CombatActionIntent intent) {
        this(actorId, intent, null, null, null, null, false, null);
    }

    public AiTurnPlan(UUID actorId, String action, TurnResourceCost cost) {
        this(actorId, new CombatActionIntent(actorId, action, cost));
    }

    public AiTurnPlan(UUID actorId, String action, TurnResourceCost cost, boolean endTurn) {
        this(actorId, endTurn ? null : new CombatActionIntent(actorId, action, cost), null, null, null, null, endTurn, null);
    }

    public static AiTurnPlan action(UUID actorId, String action, TurnResourceCost cost) {
        return new AiTurnPlan(actorId, action, cost);
    }

    public static AiTurnPlan endTurn(UUID actorId) {
        return new AiTurnPlan(actorId, null, null, null, null, null, true, null);
    }
}
