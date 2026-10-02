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
    void schedules_next_ai_when_a_large_encounter_exceeds_the_configured_step_floor() {
        UUID adventureId = UUID.randomUUID();
        var participants = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new CombatParticipant(UUID.randomUUID(), index == 11 ? "플레이어" : "적 " + index,
                        index == 11 ? CombatParticipant.Controller.PLAYER : CombatParticipant.Controller.AI,
                        12 - index, null)).toList();
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, participants);
        var workItems = new InMemoryCombatWorkItemRepository();
        var scheduler = new CombatWorkItemScheduler(workItems, 10);
        var actorId = encounter.currentParticipantId();
        var template = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null, CombatActorRole.AI,
                "END_TURN", null, UUID.randomUUID(), actorId, encounter.version(), null, null, null, null, false);

        assertEquals(CombatWorkItemScheduler.OptionalSchedule.SCHEDULED,
                scheduler.scheduleNext(template, encounter, 10,
                        com.dndmaster.adventure.application.combat.AiTacticalInstructionContext.none()));
        assertEquals(CombatWorkItemScheduler.OptionalSchedule.NOT_SCHEDULED,
                scheduler.scheduleNext(template, encounter, 24,
                        com.dndmaster.adventure.application.combat.AiTacticalInstructionContext.none()));
    }

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
