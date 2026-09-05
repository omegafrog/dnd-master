package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                   int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                   TurnResources resources, long version, long eventCursor,
                                   List<NarrativeCombatPosition> narrativePositions, PlayerReaction pendingReaction) {
    public PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                TurnResources resources, long version, long eventCursor) {
        this(encounterId, adventureId, status, round, currentParticipantId, initiative, resources, version, eventCursor, List.of(), null);
    }
    public PlayerCombatSnapshot(UUID encounterId, UUID adventureId, CombatEncounter.Status status,
                                int round, UUID currentParticipantId, List<PlayerParticipant> initiative,
                                TurnResources resources, long version, long eventCursor,
                                List<NarrativeCombatPosition> narrativePositions) {
        this(encounterId, adventureId, status, round, currentParticipantId, initiative, resources, version, eventCursor, narrativePositions, null);
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
}
