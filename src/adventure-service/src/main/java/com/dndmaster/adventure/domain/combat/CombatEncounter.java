package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record CombatEncounter(UUID encounterId, UUID adventureId, Status status, int round,
                              UUID currentParticipantId, List<CombatParticipant> participants,
                              long version, long eventCursor, List<NarrativeCombatPosition> narrativePositions) {
    public enum Status { PREPARING, ACTIVE, ENDED }
    public CombatEncounter {
        participants = List.copyOf(participants);
        narrativePositions = List.copyOf(narrativePositions == null ? List.of() : narrativePositions);
        if (encounterId == null || adventureId == null || participants.isEmpty() || round < 1 || version < 1) {
            throw new IllegalArgumentException("invalid combat encounter");
        }
    }
    public CombatEncounter(UUID encounterId, UUID adventureId, Status status, int round,
                           UUID currentParticipantId, List<CombatParticipant> participants,
                           long version, long eventCursor) {
        this(encounterId, adventureId, status, round, currentParticipantId, participants, version, eventCursor, List.of());
    }
    public CombatEncounter withEventCursor(long cursor) {
        if (cursor < 0) throw new IllegalArgumentException("event cursor must be non-negative");
        return new CombatEncounter(encounterId, adventureId, status, round, currentParticipantId,
                participants, version, cursor, narrativePositions);
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
                updatedParticipants, version + 1, eventCursor + 2, narrativePositions);
    }

    public CombatEncounter commitMovement(UUID actorId, TurnResources.Reservation reservation,
                                          NarrativeCombatPosition position) {
        if (!currentParticipantId.equals(actorId)) throw new IllegalStateException("NOT_CURRENT_ACTOR");
        CombatParticipant actor = currentParticipant();
        CombatParticipant updated = actor.withResources(actor.resources().commit(reservation));
        List<CombatParticipant> updatedParticipants = participants.stream()
                .map(p -> p.participantId().equals(actorId) ? updated : p).toList();
        List<NarrativeCombatPosition> updatedPositions = position == null
                ? narrativePositions : java.util.stream.Stream.concat(
                        narrativePositions.stream().filter(existing -> !(existing.subjectId().equals(position.subjectId())
                                && existing.targetId().equals(position.targetId()))), java.util.stream.Stream.of(position)).toList();
        return new CombatEncounter(encounterId, adventureId, status, round, currentParticipantId,
                updatedParticipants, version + 1, eventCursor + 1, updatedPositions);
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
