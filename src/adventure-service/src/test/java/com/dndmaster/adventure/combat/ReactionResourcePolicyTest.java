package com.dndmaster.adventure.combat;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionResourcePolicyTest {
    @Test void use_consumes_reaction_once_and_next_turn_resets_it() {
        UUID hero = UUID.randomUUID();
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(hero, "Hero", CombatParticipant.Controller.PLAYER, 15, null)));
        var reaction = new ReactionInterrupt(UUID.randomUUID(), "trigger", hero, UUID.randomUUID(), "effect", List.of());
        var used = encounter.requestReaction(reaction, encounter.version()).resolveReaction(reaction.reactionId(), hero, ReactionChoice.USE, encounter.version() + 1);
        assertFalse(used.currentParticipant().resources().reactionAvailable());
        assertThrows(IllegalStateException.class, () -> used.requestReaction(
                new ReactionInterrupt(UUID.randomUUID(), "again", hero, UUID.randomUUID(), "effect", List.of()), used.version()));
        var reset = used.endCurrentTurn(used.version());
        assertTrue(reset.currentParticipant().resources().reactionAvailable());
    }
}
