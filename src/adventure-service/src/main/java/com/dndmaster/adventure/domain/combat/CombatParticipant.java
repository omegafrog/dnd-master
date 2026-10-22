package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public record CombatParticipant(UUID participantId, String displayName, Controller controller,
                                int initiative, String publicCondition, TurnResources resources,
                                CombatEnemyStatBlock statBlock, Integer currentHitPoints, String enemyKind,
                                boolean surprised) {
    public enum Controller { PLAYER, AI }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition) {
        this(participantId, displayName, controller, initiative, publicCondition, TurnResources.initial(), null, null, null, false);
    }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition, TurnResources resources) {
        this(participantId, displayName, controller, initiative, publicCondition, resources, null, null, null, false);
    }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition, TurnResources resources,
                             CombatEnemyStatBlock statBlock) {
        this(participantId, displayName, controller, initiative, publicCondition, resources, statBlock,
                statBlock == null ? null : statBlock.hitPointMaximum(), null, false);
    }

    public CombatParticipant(UUID participantId, String displayName, Controller controller,
                             int initiative, String publicCondition, TurnResources resources,
                             CombatEnemyStatBlock statBlock, Integer currentHitPoints) {
        this(participantId, displayName, controller, initiative, publicCondition, resources, statBlock, currentHitPoints, null, false);
    }

    public CombatParticipant(UUID participantId, String displayName, Controller controller, int initiative,
            String publicCondition, TurnResources resources, CombatEnemyStatBlock statBlock,
            Integer currentHitPoints, String enemyKind) {
        this(participantId, displayName, controller, initiative, publicCondition, resources, statBlock, currentHitPoints, enemyKind, false);
    }

    public CombatParticipant {
        if (participantId == null || displayName == null || displayName.isBlank() || controller == null) {
            throw new IllegalArgumentException("participant identity is required");
        }
        if (resources == null) throw new IllegalArgumentException("turn resources are required");
        enemyKind = enemyKind == null || enemyKind.isBlank() ? null : enemyKind.trim().toLowerCase(java.util.Locale.ROOT);
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
                statBlock, currentHitPoints, enemyKind, surprised);
    }

    public CombatParticipant withPublicCondition(String updated) {
        return new CombatParticipant(participantId, displayName, controller, initiative, updated, resources,
                statBlock, currentHitPoints, enemyKind, surprised);
    }

    public CombatParticipant withCurrentHitPoints(int updated) {
        if (statBlock == null) throw new IllegalStateException("participant has no enemy combat numbers");
        return new CombatParticipant(participantId, displayName, controller, initiative, publicCondition, resources,
                statBlock, updated, enemyKind, surprised);
    }

    public CombatParticipant withEnemySheet(CombatEnemyStatBlock prepared) {
        if (enemyKind == null || prepared == null) throw new IllegalStateException("participant enemy kind and prepared combat numbers are required");
        return new CombatParticipant(participantId, displayName, controller, initiative, publicCondition, TurnResources.initial(),
                prepared, prepared.hitPointMaximum(), enemyKind, surprised);
    }

    public CombatParticipant withSurprised(boolean updated) {
        return new CombatParticipant(participantId, displayName, controller, initiative, publicCondition, resources,
                statBlock, currentHitPoints, enemyKind, updated);
    }

    public boolean isDefeated() {
        return (statBlock != null && currentHitPoints != null && currentHitPoints == 0)
                || (controller == Controller.PLAYER && "dead".equalsIgnoreCase(publicCondition));
    }
}
