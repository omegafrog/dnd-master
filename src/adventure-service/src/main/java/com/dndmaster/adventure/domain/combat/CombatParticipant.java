package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public record CombatParticipant(UUID participantId, String displayName, Controller controller,
                                int initiative, String publicCondition) {
    public enum Controller { PLAYER, AI }
    public CombatParticipant {
        if (participantId == null || displayName == null || displayName.isBlank() || controller == null) {
            throw new IllegalArgumentException("participant identity is required");
        }
    }
}
