package com.dndmaster.adventure.combat;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionResumePolicyTest {
    @Test void use_and_pass_resume_same_operation_step_without_new_effect() {
        UUID hero = UUID.randomUUID();
        var base = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(hero, "Hero", CombatParticipant.Controller.PLAYER, 15, null)));
        var operation = UUID.randomUUID();
        var reaction = new ReactionInterrupt(UUID.randomUUID(), "trigger", hero, operation, "character", List.of());
        var pending = base.requestReaction(reaction, base.version());
        var resumed = pending.resolveReaction(reaction.reactionId(), hero, ReactionChoice.PASS, pending.version());
        assertEquals(CombatEncounter.Status.ACTIVE, resumed.status());
        assertEquals(base.currentParticipantId(), resumed.currentParticipantId());
        assertEquals(operation, reaction.suspendedOperationId());
        assertEquals("character", CombatResumePolicy.resumeStep(reaction));
        assertNull(resumed.pendingReaction());
        assertEquals(pending.version() + 1, resumed.version());
    }
}
