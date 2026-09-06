package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public record CombatParticipant(UUID participantId, String displayName, Controller controller,
                                int initiative, String publicCondition, TurnResources resources,
                                CombatEnemyStatBlock statBlock, Integer currentHitPoints) {
    public enum Controller { PLAYER, AI }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition) {
        this(participantId, displayName, controller, initiative, publicCondition, TurnResources.initial());
    }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition, TurnResources resources) {
        this(participantId, displayName, controller, initiative, publicCondition, resources, null);
    }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition, TurnResources resources,
                             CombatEnemyStatBlock statBlock) {
        this(participantId, displayName, controller, initiative, publicCondition, resources, statBlock,
                statBlock == null ? null : statBlock.hitPointMaximum());
    }

    public CombatParticipant {
        if (participantId == null || displayName == null || displayName.isBlank() || controller == null) {
            throw new IllegalArgumentException("participant identity is required");
        }
        if (resources == null) throw new IllegalArgumentException("turn resources are required");
        if (statBlock == null && currentHitPoints != null) {
            throw new IllegalArgumentException("current hit points require enemy combat numbers");
        }
        if (statBlock != null && (currentHitPoints == null
                || currentHitPoints < 0 || currentHitPoints > statBlock.hitPointMaximum())) {
            throw new IllegalArgumentException("current hit points are outside the enemy maximum");
        }
    }

    public CombatParticipant withResources(TurnResources updated) {
        return new CombatParticipant(participantId, displayName, controller, initiative, publicCondition, updated,
                statBlock, currentHitPoints);
    }

    public CombatParticipant withCurrentHitPoints(int updated) {
        if (statBlock == null) throw new IllegalStateException("participant has no enemy combat numbers");
        return new CombatParticipant(participantId, displayName, controller, initiative, publicCondition, resources,
                statBlock, updated);
    }

    public boolean isDefeated() {
        return statBlock != null && currentHitPoints != null && currentHitPoints == 0;
    }
}
