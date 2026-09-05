package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Creates the next durable AI unit after a committed action or turn end. */
public final class CombatWorkItemScheduler {
    private final CombatWorkItemRepository workItems;
    private final int maxSteps;

    public CombatWorkItemScheduler(CombatWorkItemRepository workItems, int maxSteps) {
        this.workItems = Objects.requireNonNull(workItems);
        if (maxSteps < 1) throw new IllegalArgumentException("max steps must be positive");
        this.maxSteps = maxSteps;
    }

    public OptionalSchedule scheduleNext(CombatActionCommand template, CombatEncounter encounter,
                                         int completedSteps, AiTacticalInstructionContext instruction) {
        if (encounter.status() != CombatEncounter.Status.ACTIVE
                || encounter.currentParticipant().controller() != CombatParticipant.Controller.AI
                || completedSteps >= maxSteps) return OptionalSchedule.NOT_SCHEDULED;
        UUID actorId = encounter.currentParticipantId();
        UUID operationId = UUID.randomUUID();
        CombatActionCommand command = new CombatActionCommand(operationId, template.adventureId(), template.sessionId(),
                template.ruleSetId(), new CharacterSheetId(actorId), template.combatMapId(), CombatActorRole.AI,
                "AI_TURN", null, null, actorId, encounter.version(), null, null, null, null, false,
                null, null, template.mapVersion());
        workItems.enqueue(new CombatWorkItem(UUID.randomUUID(), encounter.encounterId(), operationId,
                encounter.version(), CombatWorkItem.WorkType.AI_TURN, Instant.now(), 0, instruction, command,
                completedSteps));
        return OptionalSchedule.SCHEDULED;
    }

    public enum OptionalSchedule { SCHEDULED, NOT_SCHEDULED }
}
