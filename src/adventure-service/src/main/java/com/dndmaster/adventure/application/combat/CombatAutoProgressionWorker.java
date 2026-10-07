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
            if (item.decisionPlan() != null) {
                plan = item.decisionPlan();
                actorId = plan.actorId();
                AiCombatTurnContext context = contextForStoredProposal(encounter, actorId, item, base);
                plan = endTurnWhenNoActionIsAvailable(plan, context);
                validateDecisionEvidence(plan, context);
                command = commandForPlan(base, plan, actorId, encounter);
            } else if ("AI_TURN".equals(base.action())) {
                AiCombatTurnContext context = buildDecisionContext(encounter, actorId, item, base, true);
                plan = Objects.requireNonNull(decisions.planTurn(context), "AI turn plan must not be null");
                AiCombatTurnContext initialContext = context;
                context = contextWithFallbackEvidence(context, plan, base);
                if (context != initialContext) plan = Objects.requireNonNull(decisions.planTurn(context),
                        "AI combat proposal with Rulebook evidence must not be null");
                plan = endTurnWhenNoActionIsAvailable(plan, context);
                if (!actorId.equals(plan.actorId())) throw new CombatCommandRejectedException(
                        "ACTION_NOT_ALLOWED", java.util.List.of("AI_PLAN_ACTOR_MISMATCH"));
                if (!plan.endTurn()) devLog("dev_combat_ai_action_proposed encounterId={} actorId={} actorType={} actionLabel={}",
                        encounter.encounterId(), actorId, context.actor().enemyKind() == null ? "COMPANION" : "ENEMY",
                        safeActionLabel(plan.intent().action()));
                validateDecisionEvidence(plan, context);
                command = commandForPlan(base, plan, actorId, encounter);
                item = item.withDecisionPlan(plan).withCommand(command);
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
        } catch (CombatCommandRejectedException failure) {
            CombatWorkItem failed = item.failed(item.leaseToken(), failure.code());
            workItems.save(failed);
            devLog("dev_combat_ai_follow_up_failed requestId={} operationId={} encounterId={} failureCode={} violations={}",
                    item.aiRequestId(), item.operationId(), item.encounterId(), failure.code(), failure.violations());
            releaseInitialRequest(failed, failure.code());
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

    private AiCombatTurnContext contextForStoredProposal(CombatEncounter encounter, UUID actorId,
            CombatWorkItem item, CombatActionCommand base) {
        CombatParticipant actor = encounter.participants().stream().filter(value -> value.participantId().equals(actorId))
                .findFirst().orElseThrow(() -> new IllegalStateException("saved AI actor is no longer in this encounter"));
        AiCombatTurnContext context = buildDecisionContext(encounter, actor, item, base);
        return item.decisionPlan() == null ? context : contextWithFallbackEvidence(context, item.decisionPlan(), base);
    }

    private AiCombatTurnContext contextWithFallbackEvidence(AiCombatTurnContext context, AiTurnPlan plan,
            CombatActionCommand base) {
        if (runtimeTurnService == null || context.actor().enemyKind() != null || context.ruleEvidence().size() > 0
                || (!plan.citationKeys().isEmpty()
                && plan.citationKeys().stream().allMatch(sheetCitationKeys(context.characterSheetJson())::contains))) return context;
        var evidence = runtimeTurnService.combatRuleEvidenceForTurn(base.adventureId(), context.actor().participantId(),
                plan.endTurn() ? "combat action assessment" : plan.intent().action());
        if (evidence.isEmpty()) throw new com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException(
                "COMBAT_RULE_EVIDENCE_UNAVAILABLE");
        return new AiCombatTurnContext(context.encounter(), context.actor(), context.tacticalInstruction(),
                context.currentSituation(), context.characterSheetJson(), context.enemyCharacterSheet(),
                context.ownerPlayerId(), context.providerSelection(), evidence);
    }

    private static java.util.Set<String> sheetCitationKeys(String sheetJson) {
        if (sheetJson == null || sheetJson.isBlank()) return java.util.Set.of();
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(sheetJson);
            var keys = new java.util.LinkedHashSet<String>();
            collectSheetCitationKeys(root, keys);
            return java.util.Set.copyOf(keys);
        } catch (java.io.IOException malformedSheet) {
            return java.util.Set.of();
        }
    }

    private static void collectSheetCitationKeys(com.fasterxml.jackson.databind.JsonNode node, java.util.Set<String> keys) {
        if (node == null) return;
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            String name = field.getKey();
            if ("citationKey".equals(name) && field.getValue().isTextual()) keys.add(field.getValue().asText());
            else if ("citationKeys".equals(name) && field.getValue().isArray())
                field.getValue().forEach(value -> { if (value.isTextual()) keys.add(value.asText()); });
            else collectSheetCitationKeys(field.getValue(), keys);
        });
        else if (node.isArray()) node.forEach(child -> collectSheetCitationKeys(child, keys));
    }

    private AiCombatTurnContext buildDecisionContext(CombatEncounter encounter, UUID actorId,
            CombatWorkItem item, CombatActionCommand base, boolean requireCurrentActor) {
        AiCombatTurnContext context = requireCurrentActor
                ? AiTacticalInstructionPolicy.contextFor(encounter, actorId,
                        item.tacticalInstruction().instruction(), item.tacticalInstruction().constraints())
                : contextForStoredProposal(encounter, actorId, item, base);
        return buildDecisionContext(encounter, context.actor(), item, base);
    }

    private AiCombatTurnContext buildDecisionContext(CombatEncounter encounter, CombatParticipant actor,
            CombatWorkItem item, CombatActionCommand base) {
        if (runtimeTurnService == null) {
            return new AiCombatTurnContext(encounter, actor, item.tacticalInstruction());
        }
        var runtimeInputs = runtimeTurnService.combatTurnRuntimeInputs(base.adventureId(), actor.participantId());
        EnemyCharacterSheet enemySheet = actor.enemyKind() == null ? null
                : runtimeTurnService.enemyCharacterSheetForCombat(base.adventureId(), actor.enemyKind())
                        .orElseThrow(() -> new com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException(
                                "ENEMY_SHEET_RULE_EVIDENCE_UNAVAILABLE"));
        var situation = runtimeInputs.situation();
        String situationText = "장소: " + situation.location() + "\n문제: " + situation.problem()
                + "\n위협: " + situation.threat() + "\n목표: " + situation.goal();
        return new AiCombatTurnContext(encounter, actor, item.tacticalInstruction(), situationText,
                runtimeInputs.characterSheetJson(), enemySheet, runtimeInputs.ownerPlayerId(), runtimeInputs.providerSelection(),
                runtimeInputs.ruleEvidence());
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

    private static String safeActionLabel(String action) {
        if (action == null) return "<missing>";
        String safe = action.toUpperCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_ -]", "?").trim();
        return safe.length() <= 40 ? safe : safe.substring(0, 40);
    }

    private static AiTurnPlan endTurnWhenNoActionIsAvailable(AiTurnPlan plan, AiCombatTurnContext context) {
        if (plan.endTurn() || context.actor().resources().actionAvailable()) return plan;
        var citations = new java.util.LinkedHashSet<String>();
        if (context.enemyCharacterSheet() != null) {
            context.enemyCharacterSheet().actions().forEach(action -> citations.addAll(action.citationKeys()));
        } else {
            context.ruleEvidence().forEach(evidence -> citations.add(evidence.referenceKey()));
            citations.addAll(sheetCitationKeys(context.characterSheetJson()));
        }
        citations.addAll(plan.citationKeys());
        if (citations.isEmpty()) return plan;
        return AiTurnPlan.endTurn(plan.actorId(), "사용 가능한 행동 자원이 없어 차례를 종료합니다.", java.util.List.copyOf(citations));
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
            if (!available.containsAll(plan.citationKeys())) throw new com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException(
                    "AI_COMBAT_CITATION_OUTSIDE_ENEMY_SHEET_SCOPE");
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
            var available = context.ruleEvidence().stream().map(
                    com.dndmaster.adventure.application.runtime.RuntimeEvidence::referenceKey)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            available.addAll(sheetCitationKeys(context.characterSheetJson()));
            if (context.ruleEvidence().size() > 0 && plan.citationKeys().stream().anyMatch(key -> !available.contains(key))) {
                throw new com.dndmaster.adventure.application.runtime.AbsentEnemyRuleEvidenceException(
                        "AI_COMBAT_CITATION_NOT_VERIFIABLE_IN_PINNED_RULEBOOK");
            }
            if (plan.citationKeys().stream().anyMatch(key -> !available.contains(key))) {
                throw new IllegalArgumentException("AI companion proposal cites a rule outside the pinned Rulebook evidence");
            }
        }
        if (!plan.endTurn()) {
            if (plan.damageAmount() != null || plan.attackModifier() != null || plan.targetArmorClass() != null) {
                throw new IllegalArgumentException("AI proposal may not supply Runtime-resolved combat values");
            }
            String action = plan.intent().action().trim().toUpperCase(java.util.Locale.ROOT);
            boolean sourceBackedEnemyAction = context.enemyCharacterSheet() != null
                    && context.enemyCharacterSheet().actions().stream()
                    .anyMatch(candidate -> candidate.name().equalsIgnoreCase(plan.intent().action()));
            if (!sourceBackedEnemyAction && !java.util.Set.of("ATTACK", "CAST_SPELL", "DODGE", "DISENGAGE", "DASH", "HELP", "HIDE", "READY", "SEARCH", "USE_OBJECT", "MOVE")
                    .contains(action)) {
                throw new IllegalArgumentException("AI combat action is outside the Runtime action allowlist");
            }
            if (!plan.intent().cost().action() || plan.intent().cost().bonusAction()
                    || plan.intent().cost().reaction() || plan.intent().cost().movement() != 0) {
                throw new IllegalArgumentException("AI combat action uses an unsupported state-change contract");
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
