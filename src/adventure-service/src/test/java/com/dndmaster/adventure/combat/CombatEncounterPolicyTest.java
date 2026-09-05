package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEncounterPolicyTest {
    private static final UUID ADVENTURE = UUID.randomUUID();
    private static final UUID HERO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GOBLIN = UUID.fromString("00000000-0000-0000-0000-000000000002");

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
}
