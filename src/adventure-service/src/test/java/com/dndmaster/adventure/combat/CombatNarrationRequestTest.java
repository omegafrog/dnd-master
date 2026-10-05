package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatNarrationRequest;
import com.dndmaster.adventure.application.combat.ConfirmedCombatState;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import com.dndmaster.adventure.domain.combat.TurnResources;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatNarrationRequestTest {
    @Test
    void carries_only_the_confirmed_result_to_adventure_runtime() {
        CombatActionCommand command = command();
        ConfirmedCombatState state = state(7L);
        CombatNarrationRequest request = CombatNarrationRequest.postResolution(command, 7L, state, 18, "명중", "검을 휘두른다");

        assertEquals(command, request.command());
        assertEquals("검을 휘두른다", request.playerInput());
        assertEquals(7L, request.encounterVersion());
        assertEquals(18, request.diceTotal());
        assertEquals("명중", request.judgment());
        assertEquals(state, request.combatState());
    }

    @Test
    void keeps_the_canonical_result_separate_from_the_narration_request() {
        CombatNarrationRequest request = CombatNarrationRequest.postResolution(command(), 7L, state(7L), 18, "명중", "attack");

        assertEquals(7L, request.encounterVersion());
        assertEquals(18, request.diceTotal());
        assertEquals("명중", request.judgment());
    }

    @Test
    void does_not_label_an_ai_combat_action_as_player_input() {
        CombatActionCommand command = new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), null,
                CombatActorRole.AI, "attack", null, UUID.randomUUID(), UUID.randomUUID(), 6L,
                15, 4, new CharacterSheetId(UUID.randomUUID()), 6, false);

        CombatNarrationRequest request = CombatNarrationRequest.postResolution(command, 7L, state(7L), 18, "명중", null);

        assertFalse(request.hasPlayerInput());
        assertEquals("AI가 조종하는 전투 참여자", request.confirmedActor());
    }

    @Test
    void rejects_a_snapshot_from_a_different_encounter_version() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CombatNarrationRequest.postResolution(command(), 7L, state(6L), 18, "명중", "검을 휘두른다"));
    }

    @Test
    void snapshot_keeps_living_and_defeated_enemy_hit_points_internal() {
        ConfirmedCombatState state = state(7L);

        assertEquals(2, state.enemies().size());
        assertEquals(List.of(false, true), state.enemies().stream().map(ConfirmedCombatState.Enemy::defeated).toList());
        assertEquals(List.of(4, 0), state.enemies().stream().map(ConfirmedCombatState.Enemy::currentHitPoints).toList());
    }

    @Test
    void snapshot_reads_current_enemy_hp_and_omits_participants_without_enemy_numbers() {
        UUID heroId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        UUID allyId = UUID.randomUUID();
        CombatEnemyStatBlock stats = new CombatEnemyStatBlock(13, 7, 3, "1d6",
                new CombatStatBlockSource(UUID.randomUUID(), 1, "page:enemy"));
        CombatEncounter encounter = new CombatEncounter(UUID.randomUUID(), UUID.randomUUID(), CombatEncounter.Status.ACTIVE,
                2, heroId, List.of(
                new CombatParticipant(heroId, "영웅", CombatParticipant.Controller.PLAYER, 15, null),
                new CombatParticipant(enemyId, "고블린", CombatParticipant.Controller.AI, 12, null,
                        TurnResources.initial(), stats, 3),
                new CombatParticipant(allyId, "동료", CombatParticipant.Controller.AI, 8, null)), 5, 10);

        ConfirmedCombatState state = ConfirmedCombatState.from(encounter);

        assertEquals(5, state.encounterVersion());
        assertEquals(1, state.enemies().size());
        assertEquals(enemyId, state.enemies().getFirst().participantId());
        assertEquals(3, state.enemies().getFirst().currentHitPoints());
        assertEquals(7, state.enemies().getFirst().maximumHitPoints());
    }

    private static ConfirmedCombatState state(long version) {
        return new ConfirmedCombatState(version, List.of(
                new ConfirmedCombatState.Enemy(UUID.randomUUID(), "고블린", 4, 7, false),
                new ConfirmedCombatState.Enemy(UUID.randomUUID(), "거대 쥐", 0, 5, true)));
    }

    private static CombatActionCommand command() {
        return new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), null,
                CombatActorRole.PLAYER, "attack", null, UUID.randomUUID(), UUID.randomUUID(), 6L,
                15, 4, new CharacterSheetId(UUID.randomUUID()), 6, false);
    }
}
