package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

/** Claims durable AI work and advances it only through the combat action boundary. */
public final class CombatAutoProgressionWorker {
    private static final Duration LEASE = Duration.ofSeconds(30);

    private final String workerId;
    private final CombatWorkItemRepository workItems;
    private final CombatEncounterRepository encounters;
    private final AiCombatDecisionPort decisions;
    private final CombatAiActionExecutor actionExecutor;
    private final CombatAiTurnEndExecutor turnEndExecutor;
    private final int maxSteps;
    private final CombatWorkItemScheduler scheduler;

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler) {
        this.workerId = Objects.requireNonNull(workerId);
        this.workItems = Objects.requireNonNull(workItems);
        this.encounters = Objects.requireNonNull(encounters);
        this.decisions = Objects.requireNonNull(decisions);
        this.actionExecutor = Objects.requireNonNull(actionExecutor);
        this.turnEndExecutor = Objects.requireNonNull(turnEndExecutor);
        if (maxSteps < 1) throw new IllegalArgumentException("max steps must be positive");
        this.maxSteps = maxSteps;
        this.scheduler = scheduler;
    }

    @Scheduled(fixedDelayString = "${adventure.combat.auto-progression.poll-delay-ms:250}")
    public void process() { processOnce(Instant.now()); }

    public boolean processOnce(Instant now) {
        Optional<CombatWorkItem> claimed = workItems.claim(workerId, LEASE, now);
        if (claimed.isEmpty()) return false;
        CombatWorkItem item = claimed.orElseThrow();
        CombatEncounter encounter = encounters.findByEncounterId(item.encounterId()).orElse(null);
        if (encounter == null) {
            workItems.save(item.failed(item.leaseToken(), "COMBAT_NOT_FOUND"));
            return true;
        }

        AutoProgressionStopPolicy.Reason stop = AutoProgressionStopPolicy.reason(encounter,
                item.completedSteps(), maxSteps);
        if (stop == AutoProgressionStopPolicy.Reason.REACTION_PENDING) {
            workItems.save(item.defer(item.leaseToken(), now));
            return true;
        }
        if (stop != AutoProgressionStopPolicy.Reason.CONTINUE) {
            CombatWorkItem completed = item.completed(item.leaseToken());
            workItems.save(completed);
            return true;
        }

        CombatActionCommand command = null;
        try {
            CombatActionCommand base = Objects.requireNonNull(item.command(), "AI work item command is missing");
            UUID actorId = encounter.currentParticipantId();
            AiCombatTurnContext context = AiTacticalInstructionPolicy.contextFor(encounter, actorId,
                    item.tacticalInstruction().instruction(), item.tacticalInstruction().constraints());
            AiTurnPlan plan = Objects.requireNonNull(decisions.planTurn(context), "AI turn plan must not be null");
            if (!actorId.equals(plan.actorId())) throw new CombatCommandRejectedException(
                    "ACTION_NOT_ALLOWED", java.util.List.of("AI_PLAN_ACTOR_MISMATCH"));
            command = commandForPlan(base, plan, actorId, encounter.version());
            item = item.withCommand(command);
            workItems.save(item);
            if (plan.endTurn()) turnEndExecutor.execute(command);
            else actionExecutor.execute(command, plan);
            CombatWorkItem completed = item.completed(item.leaseToken());
            workItems.save(completed);
            if (scheduler != null) {
                CombatActionCommand nextTemplate = command;
                AiTacticalInstructionContext nextInstruction = item.tacticalInstruction();
                encounters.findByEncounterId(item.encounterId()).ifPresent(updated ->
                        scheduler.scheduleNext(nextTemplate, updated, completed.completedSteps() + 1,
                                nextInstruction));
            }
        } catch (RuntimeException failure) {
            if (CombatRetryPolicy.shouldRetry(failure, item.attemptCount())) {
                workItems.save(item.retry(item.leaseToken(),
                        now.plus(CombatRetryPolicy.backoffAfterAttempt(item.attemptCount())),
                        failureReason(failure)));
            } else {
                workItems.save(item.failed(item.leaseToken(), failureReason(failure)));
            }
        }
        return true;
    }

    private static CombatActionCommand commandForPlan(CombatActionCommand base, AiTurnPlan plan,
                                                       UUID actorId, long expectedVersion) {
        if (!"AI_TURN".equals(base.action())) return base;
        var intent = plan.intent();
        return new CombatActionCommand(base.operationId(), base.adventureId(), base.sessionId(), base.ruleSetId(),
                new CharacterSheetId(actorId), base.combatMapId(), CombatActorRole.AI,
                plan.endTurn() ? "END_TURN" : intent.action(), base.movementPath(), null, actorId, expectedVersion,
                plan.targetArmorClass(), plan.attackModifier(), plan.targetId() == null ? null : new CharacterSheetId(plan.targetId()),
                plan.damageAmount(), false, base.narrativePosition(), base.movementDistance(), base.mapVersion());
    }

    private static String failureReason(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
