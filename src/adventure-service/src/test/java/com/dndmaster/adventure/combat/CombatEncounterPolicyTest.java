package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.domain.combat.*;
import com.dndmaster.adventure.application.combat.CombatStartParticipantFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEncounterPolicyTest {
    private static final UUID ADVENTURE = UUID.randomUUID();
    private static final UUID HERO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GOBLIN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_GOBLIN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final CombatEnemyStatBlock GOBLIN_STATS = new CombatEnemyStatBlock(12, 7, 3, "1d6+1",
            new CombatStatBlockSource(UUID.fromString("00000000-0000-0000-0000-000000000004"), 1, "test"));

    @Test
    void combat_start_requires_committed_gm_turn_and_creates_first_turn() {
        var participants = List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 12, "wounded"),
                new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 12, "unseen"));

        assertThrows(CombatStartRejectedException.class,
                () -> CombatStartPolicy.startFromCommittedGmTurn(false, ADVENTURE, participants));

        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, participants);
        assertEquals(CombatEncounter.Status.ACTIVE, encounter.status());
        assertEquals(1, encounter.round());
        assertEquals(HERO, encounter.currentParticipantId());
        assertEquals(1, encounter.version());
    }

    @Test
    void preparing_encounter_only_becomes_active_with_every_enemy_profile_and_starts_fresh() {
        var pending = CombatStartPolicy.prepareFromCommittedGmTurn(true, ADVENTURE, List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 12, null),
                new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 11, "enemy",
                        TurnResources.initial(), null, null, "goblin")));
        assertEquals(CombatEncounter.Status.PREPARING, pending.status());
        assertThrows(IllegalArgumentException.class, () -> pending.activateWithPreparedEnemyStats(java.util.Map.of()));

        var active = pending.activateWithPreparedEnemyStats(java.util.Map.of("goblin", GOBLIN_STATS));
        var goblin = active.participants().stream().filter(p -> p.participantId().equals(GOBLIN)).findFirst().orElseThrow();
        assertEquals(CombatEncounter.Status.ACTIVE, active.status());
        assertEquals(GOBLIN_STATS.hitPointMaximum(), goblin.currentHitPoints());
        assertEquals(TurnResources.initial(), goblin.resources());
        assertEquals("goblin", goblin.enemyKind());
    }

    @Test
    void active_encounter_is_unique_per_adventure() {
        var first = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE,
                List.of(new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 10, null)));
        assertThrows(ActiveCombatEncounterException.class,
                () -> CombatStartPolicy.requireNoActiveEncounter(ADVENTURE, List.of(first)));
        assertDoesNotThrow(() -> CombatStartPolicy.requireNoActiveEncounter(UUID.randomUUID(), List.of(first)));
    }

    @Test
    void initiative_tie_break_is_deterministic_by_participant_id() {
        var order = InitiativeOrderPolicy.order(List.of(
                new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 12, null),
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 12, null)));
        assertEquals(List.of(HERO, GOBLIN), order.participantIds());
    }

    @Test
    void player_projection_does_not_leak_hidden_enemy_fields() {
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 10, "hidden exact hp=7 ac=15")));
        var snapshot = PlayerCombatProjectionPolicy.toSnapshot(encounter, HERO);
        assertEquals(2, snapshot.initiative().size());
        assertEquals("healthy", snapshot.initiative().get(0).publicCondition());
        assertNull(snapshot.initiative().get(1).publicCondition());
        assertFalse(snapshot.toString().contains("hp=7"));
        assertFalse(snapshot.toString().contains("ac=15"));
    }

    @Test
    void player_projection_omits_defeated_enemies_from_initiative() {
        var defeatedGoblin = new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI,
                10, "enemy", TurnResources.initial(), GOBLIN_STATS, 0);
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                defeatedGoblin));

        var snapshot = PlayerCombatProjectionPolicy.toSnapshot(encounter, HERO);

        assertEquals(List.of(HERO), snapshot.initiative().stream()
                .map(PlayerCombatSnapshot.PlayerParticipant::participantId).toList());
    }

    @Test
    void ending_turn_skips_defeated_enemies_without_advancing_the_round_early() {
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(GOBLIN, "Defeated Goblin", CombatParticipant.Controller.AI,
                        12, "enemy", TurnResources.initial(), GOBLIN_STATS, 0),
                new CombatParticipant(SECOND_GOBLIN, "Goblin", CombatParticipant.Controller.AI,
                        10, "enemy", TurnResources.initial(), GOBLIN_STATS, 7)));

        var next = encounter.endCurrentTurn(encounter.version());

        assertEquals(SECOND_GOBLIN, next.currentParticipantId());
        assertEquals(1, next.round());
        assertEquals(3, next.participants().size(), "defeated enemies remain in the encounter record for outcome checks");
    }

    @Test
    void ending_turn_restores_each_participants_current_speed_after_partial_movement() {
        var hero = new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 15, null,
                TurnResources.initial(25));
        var goblin = new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 10, "enemy",
                TurnResources.initial(30), GOBLIN_STATS);
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, List.of(hero, goblin));
        var reservation = encounter.reserveAction(HERO, TurnResourceCost.movementOnly(10), encounter.version());
        var moved = encounter.commitMovement(HERO, reservation, null);

        var goblinTurn = moved.endCurrentTurn(moved.version());
        var returned = goblinTurn.endCurrentTurn(goblinTurn.version());
        TurnResources restored = returned.currentParticipant().resources();

        assertEquals(25, restored.movementSpeed());
        assertEquals(25, restored.movement());
        assertEquals(0, restored.movementUsed());
    }

    @Test
    void resume_requires_same_adventure_and_preserves_snapshot_cursor() {
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE,
                List.of(new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 10, null)))
                .withEventCursor(7);
        var snapshot = PlayerCombatProjectionPolicy.toSnapshot(encounter, HERO);
        assertEquals(encounter.encounterId(), CombatResumePolicy.resume(ADVENTURE, snapshot).encounterId());
        assertEquals(7, CombatResumePolicy.resume(ADVENTURE, snapshot).eventCursor());
        assertThrows(CombatResumeRejectedException.class,
                () -> CombatResumePolicy.resume(UUID.randomUUID(), snapshot));
    }

    @Test
    void combat_skips_only_the_first_turn_of_unaware_participants() {
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 20, null)
                        .withSurprised(true),
                new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 15, "enemy",
                        TurnResources.initial(), GOBLIN_STATS),
                new CombatParticipant(SECOND_GOBLIN, "Aware scout", CombatParticipant.Controller.AI, 10, "enemy",
                        TurnResources.initial(), GOBLIN_STATS)));

        assertEquals(GOBLIN, encounter.currentParticipantId());
        var afterGoblin = encounter.endCurrentTurn(encounter.version());
        assertEquals(SECOND_GOBLIN, afterGoblin.currentParticipantId());
        assertEquals(1, afterGoblin.round());

        var afterScout = afterGoblin.endCurrentTurn(afterGoblin.version());
        assertEquals(GOBLIN, afterScout.currentParticipantId());
        assertEquals(2, afterScout.round());
        assertFalse(afterScout.participants().stream().filter(p -> p.participantId().equals(HERO)).findFirst().orElseThrow().surprised());
    }

    @Test
    void exploration_awareness_marks_only_unaware_combat_participants_surprised() {
        UUID groupId = UUID.randomUUID(), unawareHero = UUID.randomUUID();
        var proposal = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentProposal("", List.of())
                .withDiscovery(new com.dndmaster.adventure.domain.runtime.ExplorationCycle.ThreatDiscovery(
                        java.util.Set.of(HERO), false, true, false, java.util.Set.of(GOBLIN), java.util.Set.of(GOBLIN),
                        java.util.Map.of(GOBLIN, java.util.Set.of(HERO))),
                        java.util.Set.of(HERO));
        var cycle = com.dndmaster.adventure.domain.runtime.ExplorationCycle.start(UUID.randomUUID(),
                java.util.Map.of(groupId, 10)).finishGroupTurn(groupId, false).withEnvironmentProposal(proposal);
        var roster = List.of(
                new CombatParticipant(HERO, "Aware hero", CombatParticipant.Controller.PLAYER, 10, null),
                new CombatParticipant(unawareHero, "Unaware hero", CombatParticipant.Controller.PLAYER, 20, null),
                new CombatParticipant(GOBLIN, "Aware enemy", CombatParticipant.Controller.AI, 15, "enemy",
                        TurnResources.initial(), GOBLIN_STATS));

        var participants = CombatStartParticipantFactory.applyExplorationAwareness(roster, cycle,
                java.util.Map.of(GOBLIN, GOBLIN));
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, participants);

        assertEquals(GOBLIN, encounter.currentParticipantId());
        assertTrue(encounter.participants().stream().filter(p -> p.participantId().equals(unawareHero)).findFirst().orElseThrow().surprised());
        var next = encounter.endCurrentTurn(encounter.version());
        assertEquals(HERO, next.currentParticipantId());
        var afterHero = next.endCurrentTurn(next.version());
        assertEquals(GOBLIN, afterHero.currentParticipantId());
        assertFalse(afterHero.participants().stream().filter(p -> p.participantId().equals(unawareHero)).findFirst().orElseThrow().surprised());
    }

    @Test
    void legacy_exploration_cycle_without_awareness_preserves_participant_defaults_and_starts_combat() {
        UUID groupId = UUID.randomUUID();
        var cycle = com.dndmaster.adventure.domain.runtime.ExplorationCycle.start(UUID.randomUUID(),
                java.util.Map.of(groupId, 10));
        var roster = List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 10, null),
                new CombatParticipant(GOBLIN, "Goblin", CombatParticipant.Controller.AI, 15, "enemy",
                        TurnResources.initial(), GOBLIN_STATS));

        var participants = CombatStartParticipantFactory.applyExplorationAwareness(roster, cycle,
                java.util.Map.of(GOBLIN, GOBLIN));
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE, participants);

        assertEquals(CombatEncounter.Status.ACTIVE, encounter.status());
        assertFalse(encounter.participants().stream().anyMatch(CombatParticipant::surprised));
    }

    @Test
    void hostileAwarenessIsAppliedPerMapTokenWithinTheSameSide() {
        UUID groupId = UUID.randomUUID(), partialHero = UUID.randomUUID();
        UUID awareEnemy = UUID.randomUUID(), unawareEnemy = UUID.randomUUID();
        UUID awareToken = UUID.randomUUID(), unawareToken = UUID.randomUUID();
        var proposal = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentProposal("", List.of())
                .withDiscovery(new com.dndmaster.adventure.domain.runtime.ExplorationCycle.ThreatDiscovery(
                        java.util.Set.of(HERO), false, false, false, java.util.Set.of(awareToken),
                        java.util.Set.of(awareToken, unawareToken), java.util.Map.of(
                                awareToken, java.util.Set.of(HERO, partialHero),
                                unawareToken, java.util.Set.of(HERO))), java.util.Set.of(HERO, partialHero));
        var cycle = com.dndmaster.adventure.domain.runtime.ExplorationCycle.start(UUID.randomUUID(),
                java.util.Map.of(groupId, 10)).finishGroupTurn(groupId, false).withEnvironmentProposal(proposal);
        var roster = List.of(
                new CombatParticipant(HERO, "Aware of both threats", CombatParticipant.Controller.PLAYER, 10, null),
                new CombatParticipant(partialHero, "Aware of one threat", CombatParticipant.Controller.PLAYER, 8, null),
                new CombatParticipant(awareEnemy, "Aware enemy", CombatParticipant.Controller.AI, 15, "enemy",
                        TurnResources.initial(), GOBLIN_STATS),
                new CombatParticipant(unawareEnemy, "Unaware enemy", CombatParticipant.Controller.AI, 12, "enemy",
                        TurnResources.initial(), GOBLIN_STATS));

        var participants = CombatStartParticipantFactory.applyExplorationAwareness(roster, cycle,
                java.util.Map.of(awareToken, awareEnemy, unawareToken, unawareEnemy));

        assertFalse(participants.stream().filter(p -> p.participantId().equals(HERO)).findFirst().orElseThrow().surprised());
        assertTrue(participants.stream().filter(p -> p.participantId().equals(partialHero)).findFirst().orElseThrow().surprised());
        assertFalse(participants.stream().filter(p -> p.participantId().equals(awareEnemy)).findFirst().orElseThrow().surprised());
        assertTrue(participants.stream().filter(p -> p.participantId().equals(unawareEnemy)).findFirst().orElseThrow().surprised());
    }

    @Test
    void environmentAwarenessSurvivesPhaseCompletionAndIsScopedToThreatTokensUntilCombatStarts() {
        UUID groupId = UUID.randomUUID(), unawareHero = UUID.randomUUID();
        UUID awareEnemy = UUID.randomUUID(), secondEnemy = UUID.randomUUID();
        var threatTokens = java.util.Set.of(awareEnemy, secondEnemy);
        var proposal = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentProposal("", List.of())
                .withDiscovery(new com.dndmaster.adventure.domain.runtime.ExplorationCycle.ThreatDiscovery(
                        java.util.Set.of(HERO), false, false, false, java.util.Set.of(awareEnemy), threatTokens,
                        java.util.Map.of(awareEnemy, java.util.Set.of(HERO), secondEnemy, java.util.Set.of(HERO))), java.util.Set.of(HERO));
        var cycle = com.dndmaster.adventure.domain.runtime.ExplorationCycle.start(UUID.randomUUID(),
                java.util.Map.of(groupId, 10)).finishGroupTurn(groupId, false).withEnvironmentProposal(proposal);

        var completed = cycle.completeEnvironmentPhase(cycle.currentPhaseCommandId());

        assertNull(completed.pendingEnvironmentProposal());
        assertEquals(java.util.Set.of(HERO), completed.threatAwareness().awareCharactersByHostileTokenId().get(awareEnemy));
        var roster = List.of(
                new CombatParticipant(HERO, "Aware hero", CombatParticipant.Controller.PLAYER, 10, null),
                new CombatParticipant(unawareHero, "Unaware hero", CombatParticipant.Controller.PLAYER, 8, null),
                new CombatParticipant(awareEnemy, "Aware enemy", CombatParticipant.Controller.AI, 15, "enemy",
                        TurnResources.initial(), GOBLIN_STATS),
                new CombatParticipant(secondEnemy, "Unaware enemy", CombatParticipant.Controller.AI, 12, "enemy",
                        TurnResources.initial(), GOBLIN_STATS));

        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, ADVENTURE,
                CombatStartParticipantFactory.applyExplorationAwareness(roster, completed,
                        java.util.Map.of(awareEnemy, awareEnemy, secondEnemy, secondEnemy)));

        assertFalse(encounter.participants().stream().filter(p -> p.participantId().equals(HERO)).findFirst().orElseThrow().surprised());
        assertTrue(encounter.participants().stream().filter(p -> p.participantId().equals(unawareHero)).findFirst().orElseThrow().surprised());
        assertFalse(encounter.participants().stream().filter(p -> p.participantId().equals(awareEnemy)).findFirst().orElseThrow().surprised());
        assertTrue(encounter.participants().stream().filter(p -> p.participantId().equals(secondEnemy)).findFirst().orElseThrow().surprised());
        assertEquals(awareEnemy, encounter.currentParticipantId());
        assertEquals(HERO, encounter.endCurrentTurn(encounter.version()).currentParticipantId());
    }
}
