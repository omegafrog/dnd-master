package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public final class PlayerCombatProjectionPolicy {
    private PlayerCombatProjectionPolicy() {}
    public static PlayerCombatSnapshot toSnapshot(CombatEncounter encounter, UUID playerId) {
        var entries = encounter.participants().stream().map(p -> new PlayerCombatSnapshot.PlayerParticipant(
                p.participantId(), p.displayName(), p.controller(), p.initiative(),
                p.controller() == CombatParticipant.Controller.PLAYER || p.participantId().equals(playerId)
                        ? p.publicCondition() : null)).toList();
        return new PlayerCombatSnapshot(encounter.encounterId(), encounter.adventureId(), encounter.status(),
                encounter.round(), encounter.currentParticipantId(), entries, TurnResources.initial(),
                encounter.version(), encounter.eventCursor());
    }
}
