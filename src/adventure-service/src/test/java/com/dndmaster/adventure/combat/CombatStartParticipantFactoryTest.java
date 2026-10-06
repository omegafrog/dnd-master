package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndmaster.adventure.application.combat.CombatStartParticipantFactory;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatStartParticipantFactoryTest {
    @Test
    void uses_the_structured_enemy_name_from_the_gm_proposal() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        List<com.dndmaster.adventure.domain.combat.CombatParticipant> participants =
                CombatStartParticipantFactory.fromPartyAndGmProposal(adventureId, List.of(
                        new AdventurePartyMember(new CharacterSheetId(heroId), ControlMode.DIRECT,
                                true, true, true, true, true, true)),
                        List.of(new CombatEnemyProposal("instant-goblin", "Goblin", 1,
                                com.dndmaster.adventure.application.runtime.CombatStartMode.INSTANT,
                                new CombatEnemyStatBlock(15, 7, 4, "1d6 + 2",
                                        new CombatStatBlockSource(UUID.randomUUID(), 1, "page-138")))));

        assertEquals(2, participants.size());
        var enemy = participants.get(1);
        assertEquals("Goblin", enemy.displayName());
        assertEquals(com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI, enemy.controller());
        assertEquals("enemy", enemy.publicCondition());
        assertTrue(!enemy.participantId().equals(heroId));
    }

    @Test
    void rejects_combat_start_without_a_structured_enemy_name() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CombatStartParticipantFactory.fromPartyAndGmProposal(adventureId, List.of(
                        new AdventurePartyMember(new CharacterSheetId(heroId), ControlMode.DIRECT,
                                true, true, true, true, true, true)),
                        List.of()));
    }

    @Test
    void expands_the_scenario_quantity_into_individual_enemy_participants() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        List<com.dndmaster.adventure.domain.combat.CombatParticipant> participants =
                CombatStartParticipantFactory.fromPartyAndGmProposal(adventureId, List.of(
                        new AdventurePartyMember(new CharacterSheetId(heroId), ControlMode.DIRECT,
                                true, true, true, true, true, true)),
                        List.of(new CombatEnemyProposal("cellar-rat-ambush", "Giant Rat", 8,
                                com.dndmaster.adventure.application.runtime.CombatStartMode.SCENARIO,
                                new CombatEnemyStatBlock(12, 7, 4, "1d6 + 2",
                                        new CombatStatBlockSource(UUID.randomUUID(), 1, "page-135")))));

        assertEquals(9, participants.size());
        assertEquals("Giant Rat 1", participants.get(1).displayName());
        assertEquals("Giant Rat 8", participants.get(8).displayName());
        assertEquals(12, participants.get(1).statBlock().armorClass());
        assertEquals(7, participants.get(8).statBlock().hitPointMaximum());
    }

    @Test
    void reuses_profile_data_but_initializes_each_encounters_hit_points_and_resources_fresh() {
        UUID adventureId = UUID.randomUUID();
        var reusableSheet = new CombatEnemyStatBlock(12, 7, 4, "1d6+2",
                new CombatStatBlockSource(UUID.randomUUID(), 1, "p. 4"));
        var party = List.<AdventurePartyMember>of();

        var firstEncounter = CombatStartParticipantFactory.fromPartyAndGmProposal(adventureId, party,
                List.of(new CombatEnemyProposal("rat", "Giant Rat", 1,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.SCENARIO, reusableSheet)),
                ignored -> "", ignored -> 0, () -> 10);
        var damaged = firstEncounter.get(0).withCurrentHitPoints(1);
        var secondEncounter = CombatStartParticipantFactory.fromPartyAndGmProposal(adventureId, party,
                List.of(new CombatEnemyProposal("rat", "Giant Rat", 1,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.SCENARIO, reusableSheet)),
                ignored -> "", ignored -> 0, () -> 10);

        assertEquals(reusableSheet, secondEncounter.get(0).statBlock());
        assertEquals(7, secondEncounter.get(0).currentHitPoints());
        assertEquals(com.dndmaster.adventure.domain.combat.TurnResources.initial(), secondEncounter.get(0).resources());
        assertEquals(1, damaged.currentHitPoints());
    }

    @Test
    void rolls_each_participants_initiative_before_encounter_start_and_adds_their_modifier() {
        UUID adventureId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        var participants = CombatStartParticipantFactory.fromPartyAndGmProposal(adventureId, List.of(
                        new AdventurePartyMember(new CharacterSheetId(heroId), ControlMode.DIRECT,
                                true, true, true, true, true, true)),
                List.of(new CombatEnemyProposal("rat", "Giant Rat", 1,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.INSTANT,
                        new CombatEnemyStatBlock(12, 7, 4, "1d6+2",
                                new CombatStatBlockSource(UUID.randomUUID(), 1, "p. 1"), 2))),
                ignored -> "Hero", ignored -> 3, new java.util.concurrent.atomic.AtomicInteger(10)::getAndIncrement);

        assertEquals(13, participants.get(0).initiative());
        assertEquals(13, participants.get(1).initiative());
        assertNotEquals(0, participants.get(0).initiative());
    }
}
