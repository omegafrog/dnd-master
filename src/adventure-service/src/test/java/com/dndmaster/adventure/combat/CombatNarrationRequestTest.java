package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatNarrationRequest;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatNarrationRequestTest {
    @Test
    void carries_only_the_confirmed_result_to_adventure_runtime() {
        CombatActionCommand command = command();
        CombatNarrationRequest request = CombatNarrationRequest.postResolution(command, 7L, 18, "명중", "검을 휘두른다");

        assertEquals(command, request.command());
        assertEquals("검을 휘두른다", request.playerInput());
        assertEquals(7L, request.encounterVersion());
        assertEquals(18, request.diceTotal());
        assertEquals("명중", request.judgment());
    }

    @Test
    void keeps_the_canonical_result_separate_from_the_narration_request() {
        CombatNarrationRequest request = CombatNarrationRequest.postResolution(command(), 7L, 18, "명중", "attack");

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

        CombatNarrationRequest request = CombatNarrationRequest.postResolution(command, 7L, 18, "명중", null);

        assertFalse(request.hasPlayerInput());
        assertEquals("AI가 조종하는 전투 참여자", request.confirmedActor());
    }

    private static CombatActionCommand command() {
        return new CombatActionCommand(UUID.randomUUID(), AdventureId.generate(), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), null,
                CombatActorRole.PLAYER, "attack", null, UUID.randomUUID(), UUID.randomUUID(), 6L,
                15, 4, new CharacterSheetId(UUID.randomUUID()), 6, false);
    }
}
