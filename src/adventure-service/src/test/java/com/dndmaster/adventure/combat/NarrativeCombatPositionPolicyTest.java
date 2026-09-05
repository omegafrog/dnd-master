package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import com.dndmaster.adventure.domain.combat.NarrativeCombatPosition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NarrativeCombatPositionPolicyTest {
    @Test
    void mapless_movement_persists_and_projects_range_and_cover() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(enemyId, "Goblin", CombatParticipant.Controller.AI, 10, null)));
        NarrativeCombatPosition position = new NarrativeCombatPosition(heroId, enemyId, "NEAR", "HALF");

        CombatEncounter moved = encounter.commitMovement(heroId,
                encounter.reserveAction(heroId, com.dndmaster.adventure.domain.combat.TurnResourceCost.movementOnly(10), 1),
                position);

        assertEquals(position, moved.narrativePositions().getFirst());
        assertEquals(20, moved.currentParticipant().resources().movement());
        assertEquals(position, com.dndmaster.adventure.domain.combat.PlayerCombatProjectionPolicy
                .toSnapshot(moved, heroId).narrativePositions().getFirst());
    }
}
