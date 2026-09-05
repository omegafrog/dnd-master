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

    public CombatParticipant currentParticipant() {
        return participants.stream().filter(p -> p.participantId().equals(currentParticipantId))
                .findFirst().orElseThrow(() -> new IllegalStateException("current combat participant is missing"));
    }

    public TurnResources.Reservation reserveAction(UUID actorId, TurnResourceCost cost, long expectedVersion) {
        requireVersion(expectedVersion);
        if (!currentParticipantId.equals(actorId)) throw new IllegalStateException("NOT_CURRENT_ACTOR");
        return currentParticipant().resources().reserve(cost);
    }

    public CombatEncounter commitAction(UUID actorId, TurnResources.Reservation reservation) {
        if (!currentParticipantId.equals(actorId)) throw new IllegalStateException("NOT_CURRENT_ACTOR");
        CombatParticipant actor = currentParticipant();
        CombatParticipant updated = actor.withResources(actor.resources().commit(reservation));
        List<CombatParticipant> updatedParticipants = participants.stream()
                .map(p -> p.participantId().equals(actorId) ? updated : p).toList();
        return new CombatEncounter(encounterId, adventureId, status, round, currentParticipantId,
                updatedParticipants, version + 1, eventCursor + 2);
    }

    public CombatEncounter endCurrentTurn(long expectedVersion) {
        requireVersion(expectedVersion);
        int currentIndex = participants.indexOf(currentParticipant());
        int nextIndex = (currentIndex + 1) % participants.size();
        int nextRound = nextIndex == 0 ? round + 1 : round;
        UUID nextParticipantId = participants.get(nextIndex).participantId();
        List<CombatParticipant> resetParticipants = participants.stream()
                .map(p -> p.participantId().equals(nextParticipantId) ? p.withResources(TurnResources.initial()) : p)
                .toList();
        return new CombatEncounter(encounterId, adventureId, status, nextRound, nextParticipantId,
                resetParticipants, version + 1, eventCursor + 1);
    }

    private void requireVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("COMBAT_VERSION_CONFLICT");
    }
}
