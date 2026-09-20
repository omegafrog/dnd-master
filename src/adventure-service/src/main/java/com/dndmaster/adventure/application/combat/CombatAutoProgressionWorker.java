package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService;
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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CombatAutoProgressionWorker.class);

    private final String workerId;
    private final CombatWorkItemRepository workItems;
    private final CombatEncounterRepository encounters;
    private final AiCombatDecisionPort decisions;
    private final CombatAiActionExecutor actionExecutor;
    private final CombatAiTurnEndExecutor turnEndExecutor;
    private final int maxSteps;
    private final CombatWorkItemScheduler scheduler;
    private final AdventureAiRequestApplicationService aiRequestService;

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps, null, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps, scheduler, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler,
                                       AdventureAiRequestApplicationService aiRequestService) {
        this.workerId = Objects.requireNonNull(workerId);
        this.workItems = Objects.requireNonNull(workItems);
        this.encounters = Objects.requireNonNull(encounters);
        this.decisions = Objects.requireNonNull(decisions);
        this.actionExecutor = Objects.requireNonNull(actionExecutor);
        this.turnEndExecutor = Objects.requireNonNull(turnEndExecutor);
        if (maxSteps < 1) throw new IllegalArgumentException("max steps must be positive");
        this.maxSteps = maxSteps;
        this.scheduler = scheduler;
        this.aiRequestService = aiRequestService;
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
            releaseInitialRequest(item, "COMBAT_NOT_FOUND");
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
            releaseInitialRequest(completed, "AI_FOLLOW_UP_COMPLETE");
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
                var next = encounters.findByEncounterId(item.encounterId()).map(updated ->
                        scheduler.scheduleNext(nextTemplate, updated, completed.completedSteps() + 1,
                                nextInstruction, completed.aiRequestId()))
                        .orElse(CombatWorkItemScheduler.OptionalSchedule.NOT_SCHEDULED);
                if (next != CombatWorkItemScheduler.OptionalSchedule.SCHEDULED) {
                    releaseInitialRequest(completed, "AI_FOLLOW_UP_COMPLETE");
                }
            } else {
                releaseInitialRequest(completed, "AI_FOLLOW_UP_COMPLETE");
            }
        } catch (RuntimeException failure) {
            workItems.save(item.failed(item.leaseToken(), failureReason(failure)));
            LOGGER.error("combat_ai_follow_up_failed requestId={} operationId={} encounterId={} exceptionClass={}",
                    item.aiRequestId(), item.operationId(), item.encounterId(), failure.getClass().getName(), failure);
            releaseInitialRequest(item, "AI_FOLLOW_UP_FAILED");
        }
        return true;
    }

    private static CombatActionCommand commandForPlan(CombatActionCommand base, AiTurnPlan plan,
                                                       UUID actorId, long expectedVersion) {
        if (!"AI_TURN".equals(base.action())) return base;
        var intent = plan.intent();
        return new CombatActionCommand(base.operationId(), base.adventureId(), base.sessionId(), base.ruleSetId(),
                new CharacterSheetId(actorId), base.combatMapId(), CombatActorRole.AI,
                plan.endTurn() ? "END_TURN" : intent.action(), base.movementPath(), base.ownerPlayerId(), actorId, expectedVersion,
                plan.targetArmorClass(), plan.attackModifier(), plan.targetId() == null ? null : new CharacterSheetId(plan.targetId()),
                plan.damageAmount(), false, base.narrativePosition(), base.movementDistance(), base.mapVersion());
    }

    private static String failureReason(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void releaseInitialRequest(CombatWorkItem item, String outcome) {
        if (aiRequestService == null || item.aiRequestId() == null || item.command() == null
                || item.command().ownerPlayerId() == null) return;
        boolean released = aiRequestService.release(new SessionId(item.command().sessionId()),
                new OwnerPlayerId(item.command().ownerPlayerId()), item.aiRequestId());
        LOGGER.info("combat_ai_follow_up_terminal outcome={} requestId={} operationId={} released={}",
                outcome, item.aiRequestId(), item.operationId(), released);
    }
}
