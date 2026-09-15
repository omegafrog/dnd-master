package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatWorkItemScheduler;
import com.dndmaster.adventure.application.combat.InMemoryCombatWorkItemRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatWorkItemSchedulerTest {
    @Test
    void retains_the_initial_request_id_for_the_scheduled_ai_follow_up() {
        UUID adventureId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(actorId, "적", CombatParticipant.Controller.AI, 10, null)));
        var workItems = new InMemoryCombatWorkItemRepository();
        var scheduler = new CombatWorkItemScheduler(workItems, 10);
        var template = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null, CombatActorRole.PLAYER,
                "END_TURN", null, UUID.randomUUID(), actorId, encounter.version(), null, null, null, null, false);

        assertEquals(CombatWorkItemScheduler.OptionalSchedule.SCHEDULED,
                scheduler.scheduleNext(template, encounter, 0,
                        com.dndmaster.adventure.application.combat.AiTacticalInstructionContext.none(), requestId));

        var scheduled = workItems.claim("test", Duration.ofSeconds(10), Instant.now()).orElseThrow();
        assertEquals(requestId, scheduled.aiRequestId());
        assertTrue(scheduled.command().ownerPlayerId() != null);
    }
}
