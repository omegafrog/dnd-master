package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                   int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                   TurnResources resources, long version, long eventCursor,
                                   List<NarrativeCombatPosition> narrativePositions, PlayerReaction pendingReaction,
                                   ProcessingFailure processingFailure, CombatSpellcastingProfile spellcasting,
                                   DeathSavingThrow deathSavingThrow) {
    public PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                TurnResources resources, long version, long eventCursor,
                                List<NarrativeCombatPosition> narrativePositions, PlayerReaction pendingReaction,
                                ProcessingFailure processingFailure) {
        this(encounterId, adventureId, status, round, currentParticipantId, initiative, resources, version, eventCursor,
                narrativePositions, pendingReaction, processingFailure, null, null);
    }
    public PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                TurnResources resources, long version, long eventCursor,
                                List<NarrativeCombatPosition> narrativePositions, PlayerReaction pendingReaction,
                                ProcessingFailure processingFailure, CombatSpellcastingProfile spellcasting) {
        this(encounterId, adventureId, status, round, currentParticipantId, initiative, resources, version, eventCursor,
                narrativePositions, pendingReaction, processingFailure, spellcasting, null);
    }
    public PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                TurnResources resources, long version, long eventCursor) {
        this(encounterId, adventureId, status, round, currentParticipantId, initiative, resources, version, eventCursor, List.of(), null, null, null, null);
    }
    public PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                TurnResources resources, long version, long eventCursor,
                                List<NarrativeCombatPosition> narrativePositions) {
        this(encounterId, adventureId, status, round, currentParticipantId, initiative, resources, version, eventCursor, narrativePositions, null, null, null, null);
    }
    public PlayerCombatSnapshot {
        narrativePositions = List.copyOf(narrativePositions == null ? List.of() : narrativePositions);
    }
    public record PlayerParticipant(UUID participantId, String displayName,
                                    CombatParticipant.Controller controller, int initiative,
                                    String publicCondition) {}
    public record PlayerReaction(UUID reactionId, String trigger, UUID operationId, String resumeStep,
                                 List<ReactionOption> options) {
        public PlayerReaction { options = List.copyOf(options == null ? List.of() : options); }
    }
    public record ProcessingFailure(UUID operationId, String failure, int attempts) {}
    public record DeathSavingThrow(int currentHitPoints, int successes, int failures, boolean stable, boolean dead) {}
}
