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
    private final EnemyCharacterSheetRepository enemyCharacterSheetRepository;
    private final com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService runtimeTurnService;

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps, null, null, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps, scheduler, null, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler,
                                       AdventureAiRequestApplicationService aiRequestService) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps,
                scheduler, aiRequestService, null, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler,
                                       AdventureAiRequestApplicationService aiRequestService,
                                       EnemyCharacterSheetRepository enemyCharacterSheetRepository) {
        this(workerId, workItems, encounters, decisions, actionExecutor, turnEndExecutor, maxSteps,
                scheduler, aiRequestService, enemyCharacterSheetRepository, null);
    }

    public CombatAutoProgressionWorker(String workerId, CombatWorkItemRepository workItems,
                                       CombatEncounterRepository encounters, AiCombatDecisionPort decisions,
                                       CombatAiActionExecutor actionExecutor,
                                       CombatAiTurnEndExecutor turnEndExecutor, int maxSteps,
                                       CombatWorkItemScheduler scheduler,
                                       AdventureAiRequestApplicationService aiRequestService,
                                       EnemyCharacterSheetRepository enemyCharacterSheetRepository,
                                       com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService runtimeTurnService) {
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
        this.enemyCharacterSheetRepository = enemyCharacterSheetRepository;
        this.runtimeTurnService = runtimeTurnService;
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
        if (item.workType() == CombatWorkItem.WorkType.ENEMY_SHEET_PREPARATION) {
            processEnemySheetPreparation(item, encounter, now);
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
            UUID actorId = "AI_TURN".equals(base.action()) ? encounter.currentParticipantId() : base.characterSheetId().value();
            AiTurnPlan plan;
            if ("AI_TURN".equals(base.action())) {
                AiCombatTurnContext context = AiTacticalInstructionPolicy.contextFor(encounter, actorId,
                        item.tacticalInstruction().instruction(), item.tacticalInstruction().constraints());
                if (runtimeTurnService != null) {
                    var runtimeInputs = runtimeTurnService.combatTurnRuntimeInputs(base.adventureId(), actorId);
                    EnemyCharacterSheet enemySheet = context.actor().enemyKind() == null ? null
                            : runtimeTurnService.enemyCharacterSheetForCombat(base.adventureId(), context.actor().enemyKind())
                                    .orElseThrow(() -> new com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException(
                                            "ENEMY_SHEET_RULE_EVIDENCE_UNAVAILABLE"));
                    var situation = runtimeInputs.situation();
                    String situationText = "장소: " + situation.location() + "\n문제: " + situation.problem()
                            + "\n위협: " + situation.threat() + "\n목표: " + situation.goal();
                    context = new AiCombatTurnContext(encounter, context.actor(), context.tacticalInstruction(),
                            situationText, runtimeInputs.characterSheetJson(), enemySheet,
                            runtimeInputs.ownerPlayerId(), runtimeInputs.providerSelection());
                }
                plan = Objects.requireNonNull(decisions.planTurn(context), "AI turn plan must not be null");
                if (!actorId.equals(plan.actorId())) throw new CombatCommandRejectedException(
                        "ACTION_NOT_ALLOWED", java.util.List.of("AI_PLAN_ACTOR_MISMATCH"));
                validateDecisionEvidence(plan, context);
                command = commandForPlan(base, plan, actorId, encounter);
                item = item.withCommand(command);
                workItems.save(item);
            } else if ("END_TURN".equals(base.action())) {
                plan = null; // The cited end-turn proposal was already validated and saved before its first application attempt.
                command = base;
            } else {
                plan = planForPersistedCommand(base);
                command = base;
            }
            if ("END_TURN".equals(command.action())) turnEndExecutor.execute(command);
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
        } catch (com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException failure) {
            CombatWorkItem blocked = item.failed(item.leaseToken(), "COMBAT_RULE_EVIDENCE_ABSENT");
            workItems.save(blocked);
            devLog("dev_combat_ai_follow_up_blocked requestId={} operationId={} encounterId={} failureClass={}",
                    item.aiRequestId(), item.operationId(), item.encounterId(), failure.getClass().getName());
            releaseInitialRequest(blocked, "COMBAT_RULE_EVIDENCE_ABSENT");
        } catch (RuntimeException failure) {
            int delaySeconds = Math.min(60, 1 << Math.min(item.attemptCount(), 6));
            CombatWorkItem retry = item.retry(item.leaseToken(), now.plusSeconds(delaySeconds), failureReason(failure));
            workItems.save(retry);
            devLog("dev_combat_ai_follow_up_retry requestId={} operationId={} encounterId={} attempt={} delaySeconds={} exceptionClass={}",
                    item.aiRequestId(), item.operationId(), item.encounterId(), item.attemptCount(), delaySeconds,
                    failure.getClass().getName());
        }
        return true;
    }

    private void processEnemySheetPreparation(CombatWorkItem item, CombatEncounter encounter, Instant now) {
        try {
            if (enemyCharacterSheetRepository == null || item.enemySheetPreparationRequest() == null) {
                throw new IllegalStateException("ENEMY_SHEET_PREPARATION_NOT_CONFIGURED");
            }
            if (runtimeTurnService != null) runtimeTurnService.prepareEnemySheetsForWork(item.enemySheetPreparationRequest());
            var stats = new java.util.LinkedHashMap<String, com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock>();
            for (var requested : item.enemySheetPreparationRequest().enemies()) {
                var sheet = enemyCharacterSheetRepository.find(requested.identity())
                        .orElseThrow(() -> new IllegalStateException("ENEMY_SHEET_NOT_READY"));
                if (!sheet.identity().equals(requested.identity())) throw new IllegalStateException("ENEMY_SHEET_IDENTITY_MISMATCH");
                stats.put(sheet.identity().enemyKind(), sheet.statBlock());
            }
            CombatEncounter active = encounter.status() == CombatEncounter.Status.PREPARING
                    ? encounters.save(encounter.activateWithPreparedEnemyStats(stats), encounter.version())
                    : encounter.status() == CombatEncounter.Status.ACTIVE ? encounter
                    : throwIllegalState("COMBAT_NOT_PREPARING");
            boolean scheduled = scheduler != null && item.command() != null
                    && scheduler.scheduleNext(item.command(), active, 0, AiTacticalInstructionContext.none(),
                            item.aiRequestId()) == CombatWorkItemScheduler.OptionalSchedule.SCHEDULED;
            CombatWorkItem completed = item.completed(item.leaseToken());
            workItems.save(completed);
            if (!scheduled) releaseInitialRequest(completed, "ENEMY_SHEET_PREPARATION_COMPLETE");
        } catch (com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException failure) {
            CombatWorkItem failed = item.failed(item.leaseToken(), failureReason(failure));
            workItems.save(failed);
            releaseInitialRequest(failed, "ENEMY_RULE_EVIDENCE_ABSENT");
        } catch (RuntimeException failure) {
            int delaySeconds = Math.min(60, 1 << Math.min(item.attemptCount(), 6));
            workItems.save(item.retry(item.leaseToken(), now.plusSeconds(delaySeconds), failureReason(failure)));
            LOGGER.warn("enemy_sheet_preparation_retry encounterId={} workItemId={} attempt={} reason={}",
                    item.encounterId(), item.workItemId(), item.attemptCount(), failureReason(failure));
        }
    }

    private static CombatEncounter throwIllegalState(String reason) { throw new IllegalStateException(reason); }

    private static CombatActionCommand commandForPlan(CombatActionCommand base, AiTurnPlan plan,
                                                       UUID actorId, CombatEncounter encounter) {
        if (!"AI_TURN".equals(base.action())) return base;
        var intent = plan.intent();
        Integer targetArmorClass = plan.targetArmorClass();
        if (targetArmorClass == null && plan.targetId() != null) targetArmorClass = encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(plan.targetId()))
                .map(CombatParticipant::statBlock).filter(Objects::nonNull)
                .map(com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock::armorClass).findFirst().orElse(null);
        Integer attackModifier = plan.attackModifier();
        if (attackModifier == null && encounter.currentParticipant().statBlock() != null) {
            attackModifier = encounter.currentParticipant().statBlock().attackModifier();
        }
        return new CombatActionCommand(base.operationId(), base.adventureId(), base.sessionId(), base.ruleSetId(),
                new CharacterSheetId(actorId), base.combatMapId(), CombatActorRole.AI,
                plan.endTurn() ? "END_TURN" : intent.action(), base.movementPath(), base.ownerPlayerId(), actorId, encounter.version(),
                targetArmorClass, attackModifier, plan.targetId() == null ? null : new CharacterSheetId(plan.targetId()),
                plan.damageAmount(), false, base.narrativePosition(), base.movementDistance(), base.mapVersion());
    }

    private static AiTurnPlan planForPersistedCommand(CombatActionCommand command) {
        return new AiTurnPlan(command.characterSheetId().value(),
                new com.dndmaster.adventure.domain.combat.CombatActionIntent(command.characterSheetId().value(),
                        command.action(), com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly()),
                command.targetArmorClass(), command.attackModifier(),
                command.targetCharacterSheetId() == null ? null : command.targetCharacterSheetId().value(),
                command.damageAmount(), false, null);
    }

    private static String failureReason(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void devLog(String pattern, Object... arguments) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info(pattern, arguments);
        }
    }

    private static void validateDecisionEvidence(AiTurnPlan plan, AiCombatTurnContext context) {
        if (context.currentSituation() == null) return; // Compatibility for directly constructed legacy test/adaptor contexts.
        if (plan.citationKeys().isEmpty()) throw new IllegalArgumentException("AI combat proposal has no rule citations");
        if (context.actor().enemyKind() != null) {
            EnemyCharacterSheet sheet = Objects.requireNonNull(context.enemyCharacterSheet(), "enemy character sheet is unavailable");
            var available = new java.util.LinkedHashSet<String>();
            available.add(sheet.statBlock().source().citationKey());
            sheet.abilities().forEach(ability -> available.addAll(ability.citationKeys()));
            sheet.actions().forEach(action -> available.addAll(action.citationKeys()));
            if (!available.containsAll(plan.citationKeys())) throw new IllegalArgumentException("AI combat proposal cites an unknown enemy rule");
            if (plan.endTurn()) {
                var allActionRules = sheet.actions().stream().flatMap(action -> action.citationKeys().stream()).collect(java.util.stream.Collectors.toSet());
                if (allActionRules.isEmpty() || !new java.util.HashSet<>(plan.citationKeys()).containsAll(allActionRules)) {
                    throw new IllegalArgumentException("enemy end-turn assessment did not review all available action rules");
                }
            } else {
                var selectedAction = sheet.actions().stream()
                        .filter(action -> action.name().equalsIgnoreCase(plan.intent().action())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("enemy action is not present on its source-backed character sheet"));
                if (!new java.util.HashSet<>(plan.citationKeys()).containsAll(selectedAction.citationKeys())) {
                    throw new IllegalArgumentException("enemy action proposal omitted its source citations");
                }
            }
        } else {
            if (plan.endTurn()) throw new IllegalArgumentException("AI companion may not end its turn without an action");
            String sheet = context.characterSheetJson();
            if (sheet == null || plan.citationKeys().stream().anyMatch(key -> !sheet.contains(key))) {
                throw new IllegalArgumentException("AI companion proposal cites a rule outside its character sheet");
            }
        }
        if (!plan.endTurn() && (context.actor().enemyKind() != null
                || plan.intent().action().toLowerCase(java.util.Locale.ROOT).matches(".*(attack|strike|spell).*"))
                && plan.targetId() == null) {
            throw new IllegalArgumentException("targeted AI combat action has no target");
        }
        if (plan.targetId() != null) {
            CombatParticipant target = context.encounter().participants().stream()
                    .filter(participant -> participant.participantId().equals(plan.targetId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("AI combat proposal target is not in this encounter"));
            if (target.isDefeated()) throw new IllegalArgumentException("AI combat proposal targets a defeated participant");
            boolean actorIsEnemy = context.actor().enemyKind() != null;
            boolean targetIsEnemy = target.enemyKind() != null;
            if (actorIsEnemy == targetIsEnemy) throw new IllegalArgumentException("AI combat proposal target is not opposing");
        }
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
