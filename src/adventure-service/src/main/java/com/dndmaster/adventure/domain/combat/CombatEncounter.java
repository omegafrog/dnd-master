package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record CombatEncounter(UUID encounterId, UUID adventureId, Status status, int round,
                              UUID currentParticipantId, List<CombatParticipant> participants,
                              long version, long eventCursor, List<NarrativeCombatPosition> narrativePositions,
                              ReactionInterrupt pendingReaction) {
    public enum Status { PREPARING, ACTIVE, REACTION_PENDING, ENDED }
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
        this(encounterId, adventureId, status, round, currentParticipantId, participants, version, eventCursor, List.of(), null);
    }
    public CombatEncounter(UUID encounterId, UUID adventureId, Status status, int round,
                           UUID currentParticipantId, List<CombatParticipant> participants,
                           long version, long eventCursor, List<NarrativeCombatPosition> narrativePositions) {
        this(encounterId, adventureId, status, round, currentParticipantId, participants, version, eventCursor, narrativePositions, null);
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
        if (pendingReaction != null) throw new IllegalStateException("REACTION_PENDING");
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
        if (pendingReaction != null) throw new IllegalStateException("REACTION_PENDING");
        int currentIndex = participants.indexOf(currentParticipant());
        int nextIndex = (currentIndex + 1) % participants.size();
        int nextRound = nextIndex == 0 ? round + 1 : round;
        UUID nextParticipantId = participants.get(nextIndex).participantId();
        List<CombatParticipant> resetParticipants = participants.stream()
                .map(p -> p.participantId().equals(nextParticipantId) ? p.withResources(TurnResources.initial()) : p)
                .toList();
        return new CombatEncounter(encounterId, adventureId, status, nextRound, nextParticipantId,
                resetParticipants, version + 1, eventCursor + 1, narrativePositions, null);
    }

    public CombatEncounter requestReaction(ReactionInterrupt reaction, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.ACTIVE || pendingReaction != null) throw new IllegalStateException("REACTION_ALREADY_PENDING");
        CombatParticipant eligible = participants.stream()
                .filter(p -> p.participantId().equals(reaction.eligibleActorId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("REACTION_ACTOR_NOT_IN_ENCOUNTER"));
        if (!eligible.resources().reactionAvailable()) throw new IllegalStateException("REACTION_RESOURCE_UNAVAILABLE");
        return new CombatEncounter(encounterId, adventureId, Status.REACTION_PENDING, round, currentParticipantId,
                participants, version + 1, eventCursor + 1, narrativePositions, reaction);
    }

    public CombatEncounter resolveReaction(UUID reactionId, UUID actorId, ReactionChoice choice, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.REACTION_PENDING || pendingReaction == null) throw new IllegalStateException("REACTION_NOT_PENDING");
        if (!pendingReaction.reactionId().equals(reactionId)) throw new IllegalStateException("REACTION_NOT_PENDING");
        if (!pendingReaction.eligibleActorId().equals(actorId)) throw new IllegalStateException("REACTION_NOT_ELIGIBLE");
        List<CombatParticipant> resolved = participants;
        if (choice == ReactionChoice.USE) {
            resolved = participants.stream().map(p -> p.participantId().equals(actorId)
                    ? p.withResources(p.resources().commit(p.resources().reserve(TurnResourceCost.reactionOnly()))) : p).toList();
        }
        return new CombatEncounter(encounterId, adventureId, Status.ACTIVE, round, currentParticipantId,
                resolved, version + 1, eventCursor + 1, narrativePositions, null);
    }

    private void requireVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("COMBAT_VERSION_CONFLICT");
    }
}
