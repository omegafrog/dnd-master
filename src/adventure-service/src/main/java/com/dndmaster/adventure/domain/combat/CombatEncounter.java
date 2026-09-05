package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record CombatEncounter(UUID encounterId, UUID adventureId, Status status, int round,
                              UUID currentParticipantId, List<CombatParticipant> participants,
                              long version, long eventCursor) {
    public enum Status { PREPARING, ACTIVE, ENDED }
    public CombatEncounter {
        participants = List.copyOf(participants);
        if (encounterId == null || adventureId == null || participants.isEmpty() || round < 1 || version < 1) {
            throw new IllegalArgumentException("invalid combat encounter");
        }
    }
    public CombatEncounter withEventCursor(long cursor) {
        if (cursor < 0) throw new IllegalArgumentException("event cursor must be non-negative");
        return new CombatEncounter(encounterId, adventureId, status, round, currentParticipantId,
                participants, version, cursor);
    }
}
