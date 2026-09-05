package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public record CombatParticipant(UUID participantId, String displayName, Controller controller,
                                int initiative, String publicCondition, TurnResources resources) {
    public enum Controller { PLAYER, AI }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition) {
        this(participantId, displayName, controller, initiative, publicCondition, TurnResources.initial());
    }

    public CombatParticipant {
        if (participantId == null || displayName == null || displayName.isBlank() || controller == null) {
            throw new IllegalArgumentException("participant identity is required");
        }
        if (resources == null) throw new IllegalArgumentException("turn resources are required");
    }

    public CombatParticipant withResources(TurnResources updated) {
        return new CombatParticipant(participantId, displayName, controller, initiative, publicCondition, updated);
    }
}
