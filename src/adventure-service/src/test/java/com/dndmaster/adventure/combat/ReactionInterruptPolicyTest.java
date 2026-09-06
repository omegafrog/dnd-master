package com.dndmaster.adventure.combat;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionInterruptPolicyTest {
    private static final UUID HERO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ENEMY = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void only_one_eligible_pending_reaction_is_preserved_without_turn_advance() {
        var encounter = encounter();
        var reaction = new ReactionInterrupt(UUID.randomUUID(), "enemy leaves reach", HERO, UUID.randomUUID(), "dice", List.of(new ReactionOption("opportunity-attack", "Attack")));
        var pending = encounter.requestReaction(reaction, encounter.version());
        assertEquals(CombatEncounter.Status.REACTION_PENDING, pending.status());
        assertEquals(HERO, pending.currentParticipantId());
        assertEquals(encounter.round(), pending.round());
        assertSame(reaction, pending.pendingReaction());
        assertThrows(IllegalStateException.class, () -> pending.requestReaction(reaction, pending.version()));
    }

    private static CombatEncounter encounter() {
        return CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(HERO, "Hero", CombatParticipant.Controller.PLAYER, 15, null),
                new CombatParticipant(ENEMY, "Enemy", CombatParticipant.Controller.AI, 10, null)));
    }
}
