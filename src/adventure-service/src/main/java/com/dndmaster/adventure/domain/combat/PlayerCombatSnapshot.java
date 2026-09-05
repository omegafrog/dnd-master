package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                   int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                   TurnResources resources, long version, long eventCursor) {
    public record PlayerParticipant(UUID participantId, String displayName,
                                    CombatParticipant.Controller controller, int initiative,
                                    String publicCondition) {}
}
