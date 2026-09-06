package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEnemyHitPointTest {
    @Test
    void successful_damage_reduces_the_target_enemy_and_detects_defeat() {
        UUID heroId = UUID.randomUUID();
        UUID ratId = UUID.randomUUID();
        var rat = new CombatParticipant(ratId, "거대 쥐", CombatParticipant.Controller.AI, 10, "enemy",
                com.dndmaster.adventure.domain.combat.TurnResources.initial(),
                new com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock(12, 7, 4, "1d4 + 2",
                        new CombatStatBlockSource(UUID.randomUUID(), 1, "page=1")));
        var encounter = new CombatEncounter(UUID.randomUUID(), UUID.randomUUID(), CombatEncounter.Status.ACTIVE, 1,
                heroId, List.of(new CombatParticipant(heroId, "영웅", CombatParticipant.Controller.PLAYER, 15, null), rat), 1, 1);

        var committed = encounter.commitAction(heroId, encounter.reserveAction(heroId, TurnResourceCost.actionOnly(), 1), ratId, 7);

        assertEquals(0, committed.participants().stream().filter(p -> p.participantId().equals(ratId)).findFirst().orElseThrow().currentHitPoints());
        assertTrue(committed.allEnemiesDefeated());
    }
}
