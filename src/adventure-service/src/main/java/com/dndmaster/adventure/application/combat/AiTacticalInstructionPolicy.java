package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.util.List;
import java.util.UUID;

public final class AiTacticalInstructionPolicy {
    private AiTacticalInstructionPolicy() {}

    public static AiCombatTurnContext contextFor(CombatEncounter encounter, UUID actorId,
                                                  String instruction, List<String> constraints) {
        if (encounter == null || actorId == null) throw new IllegalArgumentException("AI turn context identity is required");
        CombatParticipant actor = encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(actorId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AI actor is not in encounter"));
        if (actor.controller() != CombatParticipant.Controller.AI) throw new IllegalArgumentException("actor is not AI controlled");
        return new AiCombatTurnContext(encounter, actor,
                new AiTacticalInstructionContext(instruction, constraints));
    }
}
