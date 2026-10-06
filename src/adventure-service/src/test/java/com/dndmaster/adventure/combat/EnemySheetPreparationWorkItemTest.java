package com.dndmaster.adventure.combat;

import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemySheetPreparationWorkItemTest {
    @Test
    void keeps_scope_and_enemy_request_through_lease_retry_and_restore() {
        var identity = new EnemyCharacterSheetIdentity(UUID.randomUUID(), UUID.randomUUID(), 2, UUID.randomUUID(),
                List.of(UUID.randomUUID()), "goblin");
        var enemy = new CombatEnemyProposal("goblin-scene", "goblin", "Goblin", 2);
        var request = new EnemySheetPreparationRequest(UUID.randomUUID(), identity.adventureId(),
                List.of(new EnemySheetPreparationRequest.Enemy(identity, enemy)));
        var work = CombatWorkItem.enemySheetPreparation(UUID.randomUUID(), UUID.randomUUID(), 3,
                Instant.parse("2026-10-06T08:00:00Z"), request);
        var claimed = work.claimed(UUID.randomUUID(), Instant.parse("2026-10-06T08:01:00Z"));
        var retry = claimed.retry(claimed.leaseToken(), Instant.parse("2026-10-06T08:02:00Z"), "PROVIDER_UNAVAILABLE");
        var restored = CombatWorkItem.restore(retry.workItemId(), retry.encounterId(), retry.operationId(),
                retry.expectedEncounterVersion(), retry.workType(), retry.dueAt(), retry.attemptCount(),
                retry.status(), retry.leaseToken(), retry.leaseUntil(), retry.failure(), retry.tacticalInstruction(),
                retry.command(), retry.completedSteps(), retry.aiRequestId(), retry.enemySheetPreparationRequest());

        assertEquals(CombatWorkItem.WorkType.ENEMY_SHEET_PREPARATION, restored.workType());
        assertEquals(request, restored.enemySheetPreparationRequest());
        assertEquals(1, restored.attemptCount());
        assertEquals(CombatWorkItem.Status.PENDING, restored.status());
        var blocked = CombatWorkItem.restore(restored.workItemId(), restored.encounterId(), restored.operationId(),
                restored.expectedEncounterVersion(), restored.workType(), restored.dueAt(), restored.attemptCount(),
                CombatWorkItem.Status.FAILED, null, null, "ENEMY_SHEET_RULE_EVIDENCE_ABSENT", restored.tacticalInstruction(),
                restored.command(), restored.completedSteps(), restored.aiRequestId(), restored.enemySheetPreparationRequest());
        assertEquals("COMBAT_PREPARATION_BLOCKED", blocked.playerVisibleFailure());
    }
}
