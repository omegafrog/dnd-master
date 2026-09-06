package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public final class CombatResumePolicy {
    private CombatResumePolicy() {}
    public static PlayerCombatSnapshot resume(UUID adventureId, PlayerCombatSnapshot snapshot) {
        if (!adventureId.equals(snapshot.adventureId())) throw new CombatResumeRejectedException("combat belongs to another adventure");
        return snapshot;
    }

    public static String resumeStep(ReactionInterrupt reaction) {
        if (reaction == null) throw new CombatResumeRejectedException("reaction is not pending");
        return reaction.resumeStep();
    }
}
