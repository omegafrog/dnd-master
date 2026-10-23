package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatActionEvaluation;
import com.dndmaster.adventure.domain.combat.CombatActionIntent;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.CombatRulesEngine;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.TurnResources;
import com.dndmaster.adventure.domain.combat.CombatMovementPolicy;
import com.dndmaster.adventure.domain.combat.NarrativeCombatPosition;
import com.dndmaster.adventure.domain.combat.CombatEffectProposal;
import com.dndmaster.adventure.domain.combat.CombatMapEffect;
import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import com.dndmaster.adventure.domain.combat.FreeFormInterpretationPolicy;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Single idempotent command path for human combat actions and explicit turn end. */
public final class CombatActionApplicationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CombatActionApplicationService.class);
    private final CombatEncounterRepository encounterRepository;
    private final CombatActionOperationRepository operationRepository;
    private final CombatEventRepository eventRepository;
    private final CombatRulesEngine rulesEngine;
    private final DiceCombatPort dicePort;
    private final CharacterCombatPort characterPort;
    private final AiCombatPort aiPort;
    private final AiCombatDecisionPort decisionPort;
    private final MapMovementCoordinator movementCoordinator;
    private final CombatEndPort combatEndPort;
    private final CombatNarrationPort narrationPort;
    private com.dndmaster.adventure.application.runtime.MovementFollowUpPort movementFollowUpPort;
    private com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer movementFollowUpConsumer;
    private com.dndmaster.adventure.application.runtime.RuntimeTurnCommandRepository runtimeCommands;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private org.springframework.transaction.support.TransactionTemplate movementCommitTransactions;

    public CombatActionApplicationService(CombatEncounterRepository encounterRepository,
                                          CombatActionOperationRepository operationRepository,
                                          CombatEventRepository eventRepository,
                                          CombatRulesEngine rulesEngine, DiceCombatPort dicePort,
                                          CharacterCombatPort characterPort, AiCombatPort aiPort) {
        this(encounterRepository, operationRepository, eventRepository, rulesEngine, dicePort, characterPort, aiPort,
                command -> { }, context -> FreeFormActionPlan.narrativeOnly(
                        context.declaration().actorId(), TurnResourceCost.actionOnly(),
                        "자유 행동을 해석할 수 없습니다.", "자유 행동을 처리할 수 없습니다."),
                adventureId -> null);
    }

    public CombatActionApplicationService(CombatEncounterRepository encounterRepository,
                                          CombatActionOperationRepository operationRepository,
                                          CombatEventRepository eventRepository,
                                          CombatRulesEngine rulesEngine, DiceCombatPort dicePort,
                                          CharacterCombatPort characterPort, AiCombatPort aiPort,
                                          CombatMapPort mapPort) {
        this(encounterRepository, operationRepository, eventRepository, rulesEngine, dicePort, characterPort,
                aiPort, mapPort, context -> FreeFormActionPlan.narrativeOnly(
                        context.declaration().actorId(), TurnResourceCost.actionOnly(),
                        "자유 행동을 해석할 수 없습니다.", "자유 행동을 처리할 수 없습니다."),
                adventureId -> null);
    }

    public CombatActionApplicationService(CombatEncounterRepository encounterRepository,
                                          CombatActionOperationRepository operationRepository,
                                          CombatEventRepository eventRepository,
                                          CombatRulesEngine rulesEngine, DiceCombatPort dicePort,
                                          CharacterCombatPort characterPort, AiCombatPort aiPort,
                                          CombatMapPort mapPort, AiCombatDecisionPort decisionPort) {
        this(encounterRepository, operationRepository, eventRepository, rulesEngine, dicePort, characterPort, aiPort,
                mapPort, decisionPort, adventureId -> null);
    }

    public CombatActionApplicationService(CombatEncounterRepository encounterRepository,
                                          CombatActionOperationRepository operationRepository,
                                          CombatEventRepository eventRepository,
                                          CombatRulesEngine rulesEngine, DiceCombatPort dicePort,
                                          CharacterCombatPort characterPort, AiCombatPort aiPort,
                                          CombatMapPort mapPort, AiCombatDecisionPort decisionPort,
                                          CombatEndPort combatEndPort) {
        this(encounterRepository, operationRepository, eventRepository, rulesEngine, dicePort, characterPort, aiPort,
                mapPort, decisionPort, combatEndPort, CombatNarrationPort.disabled());
    }

    public CombatActionApplicationService(CombatEncounterRepository encounterRepository,
                                          CombatActionOperationRepository operationRepository,
                                          CombatEventRepository eventRepository,
                                          CombatRulesEngine rulesEngine, DiceCombatPort dicePort,
                                          CharacterCombatPort characterPort, AiCombatPort aiPort,
                                          CombatMapPort mapPort, AiCombatDecisionPort decisionPort,
                                          CombatEndPort combatEndPort, CombatNarrationPort narrationPort) {
        this.encounterRepository = Objects.requireNonNull(encounterRepository);
        this.operationRepository = Objects.requireNonNull(operationRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.rulesEngine = Objects.requireNonNull(rulesEngine);
        this.dicePort = Objects.requireNonNull(dicePort);
        this.characterPort = Objects.requireNonNull(characterPort);
        this.aiPort = Objects.requireNonNull(aiPort);
        this.movementCoordinator = new MapMovementCoordinator(Objects.requireNonNull(mapPort));
        this.decisionPort = Objects.requireNonNull(decisionPort);
        this.combatEndPort = Objects.requireNonNull(combatEndPort);
        this.narrationPort = Objects.requireNonNull(narrationPort);
    }

    public void setMovementFollowUpPorts(
            com.dndmaster.adventure.application.runtime.MovementFollowUpPort movementFollowUpPort,
            com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer movementFollowUpConsumer,
            com.dndmaster.adventure.application.runtime.RuntimeTurnCommandRepository runtimeCommands,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.movementFollowUpPort = Objects.requireNonNull(movementFollowUpPort);
        this.movementFollowUpConsumer = Objects.requireNonNull(movementFollowUpConsumer);
        this.runtimeCommands = Objects.requireNonNull(runtimeCommands);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public void setMovementCommitTransactionManager(
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.movementCommitTransactions = new org.springframework.transaction.support.TransactionTemplate(
                Objects.requireNonNull(transactionManager));
    }

    public CombatActionResponse submitFreeForm(FreeFormCombatCommand command) {
        Objects.requireNonNull(command, "free-form combat command must not be null");
        CombatActionOperation existing = operationRepository.findByCommandId(command.action().operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
        }

        if (FreeFormInterpretationPolicy.requestsSpellUse(command.declaration().text())) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED",
                    List.of("주문은 전투 화면의 주문 선택에서 골라 시전하세요."));
        }

        CombatEncounter encounter = activeEncounter(command.action());
        FreeFormActionPlan plan = decisionPort.interpretFreeForm(new FreeFormCombatContext(encounter, command.declaration()));
        CombatActionEvaluation evaluation = rulesEngine.validateFreeFormProposal(encounter, plan);
        if (!evaluation.accepted()) throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", evaluation.violations());
        if (!plan.actorId().equals(command.action().characterSheetId().value())) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("PROPOSAL_ACTOR_MISMATCH"));
        }

        TurnResources.Reservation reservation;
        try {
            reservation = encounter.reserveAction(plan.actorId(), evaluation.cost(), command.action().expectedVersion());
        } catch (RuntimeException exception) {
            throw new CombatCommandRejectedException("COMBAT_VERSION_CONFLICT".equals(exception.getMessage())
                    ? "COMBAT_VERSION_CONFLICT" : "ACTION_NOT_ALLOWED", List.of(exception.getMessage()));
        }
        CombatActionCommand resolved = resolvedCommand(command.action(), encounter, plan);
        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.action().operationId(), command.fingerprint(), encounter.encounterId(),
                plan.actorId(), evaluation.cost(), freeFormSteps(command.action().operationId(), plan)) : existing;
        operationRepository.save(operation);
        if (existing == null) {
            eventRepository.append(new CombatEvent(encounter.encounterId(), encounter.eventCursor() + 1,
                    "ACTION_RESERVED", "{\"operationId\":\"" + command.action().operationId() + "\",\"kind\":\"FREE_FORM\"}"));
        }
        try {
            characterPort.requireUsableCharacter(resolved);
            int diceTotal = 0;
            if (plan.requiresRoll()) {
                if (operation.diceTotal() != null && stepDone(operation, "dice")) {
                    diceTotal = operation.diceTotal();
                } else {
                    diceTotal = dicePort.roll(resolved);
                    operation.recordDiceTotal(diceTotal);
                    operation.completeStep("dice");
                    operationRepository.save(operation);
                }
            }
            applyMapEffect(operation, resolved, plan.effects().mapEffect());
            if (!stepDone(operation, "character")) {
                characterPort.applyOutcome(actorOutcome(resolved, plan), toActorOutcome(plan));
                operation.completeStep("character");
                operationRepository.save(operation);
            }
            int damage = isEnemyTarget(resolved, encounter) && plan.effects().hitPointDelta() < 0
                    ? -plan.effects().hitPointDelta() : 0;
            CombatEncounter committed = encounter.commitAction(plan.actorId(), reservation,
                    isEnemyTarget(resolved, encounter) ? plan.targetId() : null, damage);
            encounterRepository.save(committed, encounter.version());
            CombatActionResponse response = new CombatActionResponse(committed.encounterId(), command.action().operationId(),
                    committed.version(), "COMMITTED", plan.requiresRoll() ? diceTotal : null, plan.judgment(), List.of(), plan.narration());
            operation.committed(response);
            operationRepository.save(operation);
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(), "ACTION_RESOLVED",
                    "{\"operationId\":\"" + command.action().operationId() + "\",\"kind\":\"FREE_FORM\",\"judgment\":\""
                            + escape(plan.judgment()) + "\"}"));
            if (committed.allEnemiesDefeated()) {
                CombatEndResult ended = combatEndPort.endWhenEnemiesDefeated(command.action().adventureId().value());
                response = new CombatActionResponse(committed.encounterId(), command.action().operationId(),
                        ended.encounterVersion(), "COMBAT_ENDED", plan.requiresRoll() ? diceTotal : null,
                        plan.judgment(), List.of(), plan.narration());
                operation.committed(response);
                operationRepository.save(operation);
            }
            response = narrateAfterCommit(command.action(), response, command.declaration().text(), committed);
            operation.committed(response);
            operationRepository.save(operation);
            return response;
        } catch (CombatCommandRejectedException exception) {
            throw exception;
        } catch (RuntimeCombatRejectionException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw exception;
        } catch (RuntimeException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw new CombatExternalFailureException(exception);
        }
    }

    /** Resolves the currently supported first-level Magic Missile action. */
    public CombatActionResponse castSpell(CombatActionCommand command, String spellName) {
        Objects.requireNonNull(command, "combat action command must not be null");
        if (!"마법 화살".equals(spellName)) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("현재 전투에서 사용할 수 없는 주문입니다."));
        }
        CombatActionOperation existing = operationRepository.findByCommandId(command.operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint() + "|SPELL|" + spellName);
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
        }

        CombatEncounter encounter = activeEncounter(command);
        CombatActionEvaluation evaluation = rulesEngine.validateAction(encounter,
                new CombatActionIntent(command.characterSheetId().value(), "CAST_SPELL", TurnResourceCost.actionOnly()));
        if (!evaluation.accepted()) throw new CombatCommandRejectedException("COMBAT_STATE_REJECTED", evaluation.violations());
        if (command.targetCharacterSheetId() == null) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("주문 대상을 선택하세요."));
        }
        var target = encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(command.targetCharacterSheetId().value()))
                .findFirst().orElseThrow(() -> new CombatCommandRejectedException(
                        "ACTION_NOT_ALLOWED", List.of("전투에 참여 중인 대상을 선택하세요.")));
        if (target.controller() != com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI
                || target.statBlock() == null || target.isDefeated()) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("마법 화살은 전투 중인 적을 대상으로 합니다."));
        }
        var profile = characterPort.spellcastingProfile(command);
        boolean spellKnown = profile.availableSpells().stream()
                .anyMatch(spell -> spell.name().equals(spellName) && spell.level() == 1);
        if (!spellKnown) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("이 캐릭터는 마법 화살을 사용할 수 없습니다."));
        }
        if (profile.availableSlots().getOrDefault(1, 0) < 1) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("1레벨 주문 슬롯이 부족합니다."));
        }

        TurnResources.Reservation reservation;
        try {
            reservation = encounter.reserveAction(command.characterSheetId().value(), evaluation.cost(), command.expectedVersion());
        } catch (RuntimeException exception) {
            throw new CombatCommandRejectedException("COMBAT_STATE_REJECTED", List.of(exception.getMessage()));
        }
        String fingerprint = command.fingerprint() + "|SPELL|" + spellName;
        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), fingerprint, encounter.encounterId(),
                        command.characterSheetId().value(), evaluation.cost(), List.of(
                        new CombatActionStep("dice", command.operationId() + ":dice", CombatActionStep.Status.PENDING),
                        new CombatActionStep("character", command.operationId() + ":character", CombatActionStep.Status.PENDING)))
                : existing;
        operationRepository.save(operation);
        if (existing == null) eventRepository.append(new CombatEvent(encounter.encounterId(), encounter.eventCursor() + 1,
                "ACTION_RESERVED", "{\"operationId\":\"" + command.operationId() + "\",\"kind\":\"SPELL\"}"));

        try {
            characterPort.requireUsableCharacter(command);
            int damage;
            if (operation.diceTotal() != null && stepDone(operation, "dice")) {
                damage = operation.diceTotal();
            } else {
                damage = dicePort.rollDamage(command, 3, 4, 3);
                operation.recordDiceTotal(damage);
                operation.completeStep("dice");
                operationRepository.save(operation);
            }
            if (!stepDone(operation, "character")) {
                characterPort.consumeSpellSlot(command, 1);
                operation.completeStep("character");
                operationRepository.save(operation);
            }
            CombatEncounter committed = encounter.commitAction(command.characterSheetId().value(), reservation,
                    target.participantId(), damage);
            encounterRepository.save(committed, encounter.version());
            String judgment = "마법 화살 적중 · 피해 " + damage;
            CombatActionResponse response = new CombatActionResponse(committed.encounterId(), command.operationId(),
                    committed.version(), "COMMITTED", damage, judgment, List.of());
            operation.committed(response);
            operationRepository.save(operation);
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(), "ACTION_RESOLVED",
                    "{\"operationId\":\"" + command.operationId() + "\",\"kind\":\"SPELL\",\"spell\":\"마법 화살\",\"damage\":" + damage + "}"));
            response = narrateAfterCommit(command, response, "마루가 마법 화살을 1레벨 주문 슬롯으로 시전했습니다.", committed);
            operation.committed(response);
            operationRepository.save(operation);
            return response;
        } catch (CombatCommandRejectedException exception) {
            throw exception;
        } catch (RuntimeCombatRejectionException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw exception;
        } catch (RuntimeException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw new CombatExternalFailureException(exception);
        }
    }

    private static List<CombatActionStep> freeFormSteps(UUID operationId, FreeFormActionPlan plan) {
        List<CombatActionStep> steps = new java.util.ArrayList<>();
        if (plan.requiresRoll()) steps.add(new CombatActionStep("dice", operationId + ":dice", CombatActionStep.Status.PENDING));
        if (plan.effects().mapEffect() != null) steps.add(new CombatActionStep("map", operationId + ":map", CombatActionStep.Status.PENDING));
        steps.add(new CombatActionStep("character", operationId + ":character", CombatActionStep.Status.PENDING));
        return steps;
    }

    private void applyMapEffect(CombatActionOperation operation, CombatActionCommand command, CombatMapEffect effect) {
        if (effect == null || stepDone(operation, "map")) return;
        requireCommittedMovement(movementCoordinator.resolve(
                new CombatMapMoveCommand(command, effect.movementDistance(), effect.expectedVersion())));
        operation.completeStep("map");
        operationRepository.save(operation);
    }

    private static CombatActionCommand resolvedCommand(CombatActionCommand original, CombatEncounter encounter,
            FreeFormActionPlan plan) {
        CombatMapEffect map = plan.effects().mapEffect();
        String movementPath = map == null ? original.movementPath() : map.movementPath();
        Integer movementDistance = original.movementDistance();
        Long mapVersion = original.mapVersion();
        if (map != null) {
            movementDistance = map.movementDistance();
            mapVersion = map.expectedVersion();
        }
        Integer targetArmorClass = plan.targetArmorClass();
        if (targetArmorClass == null && plan.targetId() != null) {
            targetArmorClass = encounter.participants().stream()
                    .filter(participant -> participant.participantId().equals(plan.targetId()))
                    .map(com.dndmaster.adventure.domain.combat.CombatParticipant::statBlock)
                    .filter(Objects::nonNull)
                    .map(com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock::armorClass)
                    .findFirst().orElse(null);
        }
        Integer attackModifier = plan.attackModifier();
        if (attackModifier == null && encounter.currentParticipant().statBlock() != null) {
            attackModifier = encounter.currentParticipant().statBlock().attackModifier();
        }
        return new CombatActionCommand(original.operationId(), original.adventureId(), original.sessionId(), original.ruleSetId(),
                original.characterSheetId(), map == null ? original.combatMapId() : map.mapId(), original.role(), "FREE_FORM",
                movementPath, original.ownerPlayerId(),
                map == null ? original.tokenId() : map.tokenId(), original.expectedVersion(), targetArmorClass,
                attackModifier, plan.targetId() == null ? null : new com.dndmaster.adventure.domain.adventure.CharacterSheetId(plan.targetId()),
                null, false, original.narrativePosition(), movementDistance, mapVersion);
    }

    /** Character-sheet effects belong to the acting player; enemy damage belongs to the encounter. */
    private static CombatOutcome toActorOutcome(FreeFormActionPlan plan) {
        CombatEffectProposal effect = plan.effects();
        return new CombatOutcome(plan.judgment(), new CombatCharacterMutation(
                isSelfTarget(plan) ? effect.hitPointDelta() : 0,
                effect.currencyDelta(), effect.addItems(), effect.removeItems()));
    }

    private static CombatActionCommand actorOutcome(CombatActionCommand command, FreeFormActionPlan plan) {
        if (isSelfTarget(plan)) return command;
        return new CombatActionCommand(command.operationId(), command.adventureId(), command.sessionId(), command.ruleSetId(),
                command.characterSheetId(), command.combatMapId(), command.role(), command.action(), command.movementPath(),
                command.ownerPlayerId(), command.tokenId(), command.expectedVersion(), command.targetArmorClass(),
                command.attackModifier(), null, command.damageAmount(), command.endCombat(), command.narrativePosition(),
                command.movementDistance(), command.mapVersion());
    }

    private static boolean isSelfTarget(FreeFormActionPlan plan) {
        return plan.targetId() == null || plan.targetId().equals(plan.actorId());
    }

    public CombatActionResponse submit(CombatActionCommand command) {
        return submitStructured(command, TurnResourceCost.actionOnly(), false);
    }

    public CombatActionResponse submitAi(CombatActionCommand command, AiTurnPlan plan) {
        Objects.requireNonNull(command, "AI combat command must not be null");
        Objects.requireNonNull(plan, "AI turn plan must not be null");
        CombatEncounter encounter = activeEncounter(command);
        if (encounter.currentParticipant().statBlock() == null) {
            CharacterCombatStatus status = characterPort.combatStatus(command);
            if (status != null && (status.currentHitPoints() == 0 || status.dead())) {
                return endTurnAi(command);
            }
        }
        if (!command.characterSheetId().value().equals(plan.actorId())) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("AI_PLAN_ACTOR_MISMATCH"));
        }
        if (plan.endTurn()) return endTurnAi(command);
        return submitStructured(command, plan.intent().cost(), true);
    }

    private CombatActionResponse submitStructured(CombatActionCommand command, TurnResourceCost cost,
                                                  boolean aiActor) {
        Objects.requireNonNull(command, "combat command must not be null");
        CombatActionOperation existing = operationRepository.findByCommandId(command.operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.GM_INTERVENTION_PENDING
                    && existing.response() != null && existing.response().movementAdjudication() != null
                    && existing.response().movementAdjudication().waitingForPlayerInput()) {
                return existing.response();
            }
            if (existing.status() == CombatActionOperation.Status.COMMITTED) {
                if (command.isMovement() && existing.response() != null
                        && existing.response().movementAdjudication() == null
                        && command.combatMapId() != null) {
                    return resumeCommittedMovementFollowUp(command, existing);
                }
                return existing.response();
            }
            if (command.isMovement() && existing.response() != null) {
                return resumeCommittedMovementFollowUp(command, existing);
            }
        }

        CombatEncounter encounter = activeEncounter(command);
        if (existing == null && operationRepository.hasPendingForEncounter(encounter.encounterId())) {
            long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                    "combat_action_pending_guard", "commandId=" + command.operationId() + " encounterId="
                            + encounter.encounterId() + " expected=no_pending_operation actual=pending_operation");
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "combat_action_pending_guard", "commandId=" + command.operationId() + " encounterId="
                            + encounter.encounterId() + " expected=allow_new_action actual=retry_required"
                            + " recoveryAction=resume_same_pending_operation", started);
            throw new CombatCommandRejectedException("RETRY_REQUIRED", List.of("COMBAT_ACTION_RECOVERY_PENDING"));
        }
        if (command.isMovement()) return submitMovement(command, encounter, existing);
        CombatActionEvaluation evaluation = aiActor
                ? rulesEngine.validateAiAction(encounter,
                new CombatActionIntent(command.characterSheetId().value(), command.action(), cost))
                : rulesEngine.validateAction(encounter,
                new CombatActionIntent(command.characterSheetId().value(), command.action(), cost));
        if (!evaluation.accepted()) throw new CombatCommandRejectedException("COMBAT_STATE_REJECTED", evaluation.violations());

        TurnResources.Reservation reservation;
        try {
            reservation = encounter.reserveAction(command.characterSheetId().value(), evaluation.cost(), command.expectedVersion());
        } catch (RuntimeException exception) {
            String code = "COMBAT_VERSION_CONFLICT".equals(exception.getMessage())
                    ? "COMBAT_VERSION_CONFLICT" : "ACTION_NOT_ALLOWED";
            throw new CombatCommandRejectedException(code, List.of(exception.getMessage()));
        }

        boolean dash = "DASH".equalsIgnoreCase(command.action());
        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), command.fingerprint(), encounter.encounterId(),
                command.characterSheetId().value(), evaluation.cost(), structuredSteps(command.operationId(), dash))
                : existing;
        operationRepository.save(operation);
        if (existing == null) {
            eventRepository.append(new CombatEvent(encounter.encounterId(), encounter.eventCursor() + 1,
                    "ACTION_RESERVED", "{\"operationId\":\"" + command.operationId() + "\"}"));
        }

        try {
            // Source-backed enemies have no player character sheet in Character Management.
            // Their hit points and readiness are owned by this encounter's prepared enemy sheet.
            if (!aiActor || encounter.currentParticipant().enemyKind() == null) {
                characterPort.requireUsableCharacter(command);
            }
            if (dash) {
                operation.completeStep("action");
                operationRepository.save(operation);
                CombatEncounter committed = encounter.commitAction(command.characterSheetId().value(), reservation,
                        null, 0, true);
                encounterRepository.save(committed, encounter.version());
                CombatActionResponse response = new CombatActionResponse(committed.encounterId(), command.operationId(),
                        committed.version(), "COMMITTED", null, "추가 이동량을 확보했습니다.", List.of());
                operation.committed(response);
                operationRepository.save(operation);
                eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(),
                        "ACTION_RESOLVED", "{\"operationId\":\"" + command.operationId() + "\",\"kind\":\"DASH\"}"));
                return response;
            }
            CombatActionCommand resolvedCommand = aiActor
                    ? resolveAiAttack(command, encounter)
                    : resolvePlayerAttack(command, encounter);
            int diceTotal;
            if (operation.diceTotal() != null && operation.steps().stream().anyMatch(step -> step.name().equals("dice") && step.status() == CombatActionStep.Status.DONE)) {
                diceTotal = operation.diceTotal();
            } else {
                diceTotal = dicePort.roll(resolvedCommand);
                operation.recordDiceTotal(diceTotal);
                operation.completeStep("dice");
                operationRepository.save(operation);
            }
            CombatOutcome outcome = aiPort.adjudicateOutcome(resolvedCommand, diceTotal);
            if (!isEnemyTarget(resolvedCommand, encounter) && !stepDone(operation, "character")) {
                characterPort.applyOutcome(resolvedCommand, outcome);
                operation.completeStep("character");
                operationRepository.save(operation);
            }
            int damage = outcome.mutation().hitPointDelta() < 0 ? -outcome.mutation().hitPointDelta() : 0;
            CombatEncounter committed = encounter.commitAction(command.characterSheetId().value(), reservation,
                    resolvedCommand.targetCharacterSheetId() == null ? null : resolvedCommand.targetCharacterSheetId().value(), damage, dash);
            encounterRepository.save(committed, encounter.version());
            CombatActionResponse response = new CombatActionResponse(encounter.encounterId(), command.operationId(),
                    committed.version(), "COMMITTED", diceTotal, outcome.judgment(), List.of());
            operation.committed(response);
            operationRepository.save(operation);
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(),
                    "ACTION_RESOLVED", "{\"operationId\":\"" + command.operationId()
                            + "\",\"diceTotal\":" + diceTotal + ",\"judgment\":\""
                            + escape(outcome.judgment()) + "\"}"));
            if (committed.allEnemiesDefeated()) {
                CombatEndResult ended = combatEndPort.endWhenEnemiesDefeated(command.adventureId().value());
                response = new CombatActionResponse(encounter.encounterId(), command.operationId(),
                        ended.encounterVersion(), "COMBAT_ENDED", diceTotal, outcome.judgment(), List.of());
                operation.committed(response);
                operationRepository.save(operation);
            }
            response = narrateAfterCommit(command, response, aiActor ? null : command.action(), committed);
            operation.committed(response);
            operationRepository.save(operation);
            return response;
        } catch (RuntimeCombatRejectionException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw exception;
        } catch (RuntimeException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw new CombatExternalFailureException(exception);
        }
    }

    private static List<CombatActionStep> structuredSteps(UUID operationId, boolean dash) {
        if (dash) return List.of(new CombatActionStep("action", operationId + ":action", CombatActionStep.Status.PENDING));
        return List.of(new CombatActionStep("dice", operationId + ":dice", CombatActionStep.Status.PENDING),
                new CombatActionStep("character", operationId + ":character", CombatActionStep.Status.PENDING));
    }

    private CombatActionCommand resolvePlayerAttack(CombatActionCommand command, CombatEncounter encounter) {
        if (!"attack".equalsIgnoreCase(command.action()) || command.role() != CombatActorRole.PLAYER) return command;
        Integer armorClass = command.targetArmorClass();
        if (armorClass == null && command.targetCharacterSheetId() != null) {
            armorClass = encounter.participants().stream()
                    .filter(participant -> participant.participantId().equals(command.targetCharacterSheetId().value()))
                    .map(com.dndmaster.adventure.domain.combat.CombatParticipant::statBlock)
                    .filter(Objects::nonNull).map(com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock::armorClass)
                    .findFirst().orElse(null);
        }
        Integer modifier = command.attackModifier() == null ? characterPort.attackModifier(command) : command.attackModifier();
        Integer damage = command.damageAmount() == null ? characterPort.damageAmount(command) : command.damageAmount();
        return new CombatActionCommand(command.operationId(), command.adventureId(), command.sessionId(), command.ruleSetId(),
                command.characterSheetId(), command.combatMapId(), command.role(), command.action(), command.movementPath(),
                command.ownerPlayerId(), command.tokenId(), command.expectedVersion(), armorClass, modifier,
                command.targetCharacterSheetId(), damage, command.endCombat(), command.narrativePosition(),
                command.movementDistance(), command.mapVersion(), command.aimSelection());
    }

    private CombatActionCommand resolveAiAttack(CombatActionCommand command, CombatEncounter encounter) {
        if (command.targetCharacterSheetId() == null) return command;
        var actor = encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(command.characterSheetId().value()))
                .findFirst().orElse(encounter.currentParticipant());
        Integer armorClass = command.targetArmorClass();
        if (armorClass == null) {
            armorClass = encounter.participants().stream()
                    .filter(participant -> participant.participantId().equals(command.targetCharacterSheetId().value()))
                    .map(com.dndmaster.adventure.domain.combat.CombatParticipant::statBlock)
                    .filter(Objects::nonNull)
                    .map(com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock::armorClass)
                    .findFirst().orElse(null);
        }
        if (armorClass == null) armorClass = characterPort.armorClass(command, command.targetCharacterSheetId());

        Integer attackModifier = command.attackModifier();
        if (attackModifier == null && actor.statBlock() != null) attackModifier = actor.statBlock().attackModifier();
        if (attackModifier == null) attackModifier = characterPort.attackModifier(command);

        Integer damage = command.damageAmount();
        if (damage == null && actor.statBlock() != null) damage = averageDamage(actor.statBlock().damageDice());
        if (damage == null && actor.enemyKind() == null) damage = characterPort.damageAmount(command);
        return new CombatActionCommand(command.operationId(), command.adventureId(), command.sessionId(),
                command.ruleSetId(), command.characterSheetId(), command.combatMapId(), command.role(),
                command.action(), command.movementPath(), command.ownerPlayerId(), command.tokenId(),
                command.expectedVersion(), armorClass, attackModifier, command.targetCharacterSheetId(),
                damage, command.endCombat(), command.narrativePosition(), command.movementDistance(), command.mapVersion());
    }

    private static Integer averageDamage(String damageDice) {
        if (damageDice == null || damageDice.isBlank()) return null;
        var matcher = java.util.regex.Pattern.compile("(?i)(\\d+)\\s*d\\s*(\\d+)([+-]\\d+)?")
                .matcher(damageDice.replace(" ", ""));
        if (!matcher.matches()) return null;
        int diceCount = Integer.parseInt(matcher.group(1));
        int dieSides = Integer.parseInt(matcher.group(2));
        int modifier = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return Math.max(1, (diceCount * (dieSides + 1)) / 2 + modifier);
    }

    private static boolean isEnemyTarget(CombatActionCommand command, CombatEncounter encounter) {
        return command.targetCharacterSheetId() != null && encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(command.targetCharacterSheetId().value()))
                .anyMatch(participant -> participant.controller() == com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI
                        && participant.statBlock() != null);
    }

    private CombatActionResponse submitMovement(CombatActionCommand command, CombatEncounter encounter,
                                                 CombatActionOperation existing) {
        int distance = movementDistance(command);
        if (command.role() != CombatActorRole.PLAYER) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("PLAYER_MOVEMENT_REQUIRED"));
        }
        if (command.combatMapId() != null && command.movementPath() == null) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("MOVEMENT_PATH_REQUIRED"));
        }
        if (command.combatMapId() != null && command.narrativePosition() != null) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("MAPLESS_POSITION_NOT_ALLOWED"));
        }
        CombatActionEvaluation evaluation = rulesEngine.validateAction(encounter,
                new CombatActionIntent(command.characterSheetId().value(), command.action(),
                        TurnResourceCost.movementOnly(distance)));
        if (!evaluation.accepted()) throw new CombatCommandRejectedException("COMBAT_STATE_REJECTED", evaluation.violations());
        validateNarrativePosition(command, encounter);
        TurnResources.Reservation reservation;
        try {
            reservation = encounter.reserveAction(command.characterSheetId().value(), evaluation.cost(), command.expectedVersion());
        } catch (RuntimeException exception) {
            throw new CombatCommandRejectedException("COMBAT_VERSION_CONFLICT".equals(exception.getMessage())
                    ? "COMBAT_VERSION_CONFLICT" : "ACTION_NOT_ALLOWED", List.of(exception.getMessage()));
        }

        UUID mapOperationId = existing == null ? UUID.randomUUID()
                : mapOperationId(existing);
        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), command.fingerprint(), encounter.encounterId(),
                command.characterSheetId().value(), evaluation.cost(), List.of(
                new CombatActionStep(command.combatMapId() == null ? "narrative" : "map",
                        command.combatMapId() == null ? command.operationId() + ":narrative" : mapOperationId.toString(),
                        CombatActionStep.Status.PENDING)))
                : existing;
        operationRepository.save(operation);
        int committedDistance = distance;
        CombatMapMoveResult movement = null;
        boolean movementCommitComplete = false;
        try {
            if (command.combatMapId() != null && !stepDone(operation, "map")) {
                movement = movementCoordinator.resolve(
                        new CombatMapMoveCommand(withOperationId(command, mapOperationId), distance, expectedMapVersion(command)));
                requireCommittedMovement(movement);
                if (movement.status() == CombatMapMovementStatus.INTERRUPTED) {
                    committedDistance = traversedMovementDistance(movement);
                    reservation = encounter.reserveAction(command.characterSheetId().value(),
                            TurnResourceCost.movementOnly(committedDistance), command.expectedVersion());
                }
                operation.completeStep("map");
                operationRepository.save(operation);
            } else if (command.combatMapId() != null) {
                movement = movementCoordinator.query(command.combatMapId(), mapOperationId);
                requireCommittedMovement(movement);
                if (movement.status() == CombatMapMovementStatus.INTERRUPTED) {
                    committedDistance = traversedMovementDistance(movement);
                    reservation = encounter.reserveAction(command.characterSheetId().value(),
                            TurnResourceCost.movementOnly(committedDistance), command.expectedVersion());
                }
            } else if (command.combatMapId() == null) {
                operation.completeStep("narrative");
                operationRepository.save(operation);
            }
            CombatEncounter committed = encounter.commitMovement(command.characterSheetId().value(), reservation,
                    command.narrativePosition());
            String judgment = command.narrativePosition() == null
                    ? "moved " + committedDistance + "ft" : command.narrativePosition().rangeBand()
                    + " range, " + command.narrativePosition().cover() + " cover";
            CombatActionResponse response = new CombatActionResponse(committed.encounterId(), command.operationId(),
                    committed.version(), "MOVE_COMMITTED", null, judgment, List.of());
            if (requiresMovementAdjudication(movement)) operation.awaitingMovementAdjudication(response);
            else operation.committed(response);
            commitMovementResult(committed, encounter.version(), operation, committedDistance);
            movementCommitComplete = true;
            if (movement != null) {
                response = attachMovementAdjudication(command, committed, operation, movement, response);
                if (operation.status() != CombatActionOperation.Status.GM_INTERVENTION_PENDING) {
                    operation.committed(response);
                    operationRepository.save(operation);
                }
            }
            if (operation.status() == CombatActionOperation.Status.GM_INTERVENTION_PENDING) return response;
            response = narrateAfterCommit(command, response, command.action(), committed);
            operation.committed(response);
            operationRepository.save(operation);
            return response;
        } catch (CombatCommandRejectedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (movementCommitComplete) operation.failed(exception);
            else operation.failedBeforeCommit(exception);
            operationRepository.save(operation);
            throw new CombatExternalFailureException(exception);
        }
    }

    public CombatActionResponse submitMovementPlayerRoll(UUID adventureId, UUID operationId, UUID pendingTurnId,
            int result, long expectedVersion) {
        CombatActionOperation operation = operationRepository.findByCommandId(operationId)
                .orElseThrow(() -> new CombatCommandRejectedException("RETRY_REQUIRED", List.of("MOVEMENT_ROLL_NOT_PENDING")));
        CombatEncounter encounter = encounterRepository.findByEncounterId(operation.encounterId())
                .orElseThrow(() -> new CombatCommandRejectedException("COMBAT_NOT_ACTIVE", List.of("COMBAT_NOT_ACTIVE")));
        CombatActionResponse pending = operation.response();
        var adjudication = pending == null ? null : pending.movementAdjudication();
        if (operation.status() == CombatActionOperation.Status.COMMITTED && adjudication != null
                && adjudication.turnId().equals(pendingTurnId) && Objects.equals(operation.diceTotal(), result)) {
            return pending;
        }
        if (!encounter.adventureId().equals(adventureId)
                || operation.status() != CombatActionOperation.Status.GM_INTERVENTION_PENDING
                || adjudication == null || !adjudication.waitingForPlayerInput()
                || !adjudication.turnId().equals(pendingTurnId)
                || !encounter.currentParticipantId().equals(operation.actorId())
                || pending.encounterVersion() != expectedVersion || encounter.version() != expectedVersion) {
            throw new CombatCommandRejectedException("RETRY_REQUIRED", List.of("MOVEMENT_ROLL_NOT_PENDING"));
        }
        if (result < 1 || result > 20 || !"1d20".equalsIgnoreCase(adjudication.playerInput().diceExpression())) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("INVALID_PLAYER_ROLL"));
        }
        try {
            operation.recordDiceTotal(result);
        } catch (IllegalStateException mismatch) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("MOVEMENT_ROLL_ALREADY_RECORDED"));
        }
        operationRepository.save(operation);
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "combat_action_movement_player_roll", "commandId=" + operation.commandId() + " operationId="
                        + operation.mapOperationId() + " turnId=" + pendingTurnId + " expected=recorded_required_roll actual=submitted");
        try {
            MovementFollowUpCommand followUp = Objects.requireNonNull(operation.movementFollowUp(),
                    "pending movement follow-up facts are missing").afterPlayerRoll(adjudication.playerInput(), result);
            UUID sourceId = UUID.nameUUIDFromBytes(("combat-action-movement-source:" + operation.mapOperationId())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            com.dndmaster.adventure.application.runtime.RuntimeTurnCommand source = Objects.requireNonNull(runtimeCommands, "runtime command repository is not configured")
                    .findByCommandId(sourceId).orElseThrow(() -> new IllegalStateException("movement source command is missing"));
            var published = movementFollowUpPort.publish(followUp, adventureId, source.sessionId(), source.ownerPlayerId());
            if (published.status() != com.dndmaster.adventure.application.runtime.MovementFollowUpPort.Result.Status.DONE) {
                throw new IllegalStateException("final movement ruling could not be persisted: " + published.value());
            }
            var consumed = movementFollowUpConsumer.consume(source, followUp);
            if (consumed.status() != com.dndmaster.adventure.application.runtime.MovementFollowUpPort.Result.Status.DONE) {
                throw new IllegalStateException("final movement ruling did not complete: " + consumed.value());
            }
            final com.dndmaster.adventure.application.runtime.CombatContinuationCommand finalRuling;
            try {
                finalRuling = objectMapper.readValue(consumed.value(),
                        com.dndmaster.adventure.application.runtime.CombatContinuationCommand.class);
            } catch (java.io.IOException malformed) {
                throw new IllegalStateException("stored final movement ruling is corrupt", malformed);
            }
            UUID expectedContinuationId = UUID.nameUUIDFromBytes(("movement-continuation:" + followUp.commandId())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!finalRuling.commandId().equals(expectedContinuationId) || !finalRuling.turnId().equals(pendingTurnId)
                    || !finalRuling.operationId().equals(operation.mapOperationId())
                    || !finalRuling.trigger().equals(adjudication.trigger()) || finalRuling.waitingForPlayerInput()) {
                throw new IllegalStateException("final movement ruling identity or state is invalid");
            }
            CombatActionResponse response = new CombatActionResponse(pending.encounterId(), operation.commandId(),
                    pending.encounterVersion(), pending.status(), result, finalRuling.judgment(), pending.violations(),
                    finalRuling.narration(), finalRuling);
            operation.committed(response);
            operationRepository.save(operation);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "combat_action_movement_player_roll", "commandId=" + operation.commandId() + " operationId="
                            + operation.mapOperationId() + " turnId=" + pendingTurnId
                            + " expected=final_ruling_and_follow_up_saved actual=committed recoveryAction=replay_final_response", started);
            return response;
        } catch (RuntimeException | Error failure) {
            String failureState = "expected=final_ruling_and_follow_up_saved actual=failed";
            if (operation.status() == CombatActionOperation.Status.COMMITTED) {
                CombatActionOperation durable = operationRepository.findByCommandId(operationId).orElse(null);
                if (durable != null && durable.status() == CombatActionOperation.Status.COMMITTED) {
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                            "combat_action_movement_player_roll", "commandId=" + operation.commandId() + " operationId="
                                    + operation.mapOperationId() + " turnId=" + pendingTurnId
                                    + " expected=final_ruling_and_follow_up_saved actual=committed recoveryAction=replay_final_response", started);
                    return durable.response();
                }
                operation.awaitingMovementAdjudication(pending, operation.movementFollowUp());
                failureState = "expected=COMMITTED actual=GM_INTERVENTION_PENDING";
            } else {
                operation.failed(failure);
            }
            operationRepository.save(operation);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "combat_action_movement_player_roll", "commandId=" + operation.commandId() + " operationId="
                            + operation.mapOperationId() + " turnId=" + pendingTurnId
                            + " " + failureState + " recoveryAction=retry_same_operation", started, failure);
            throw failure instanceof CombatCommandRejectedException rejected ? rejected : new CombatExternalFailureException(failure);
        }
    }

    public CombatActionResponse pendingMovementAdjudication(UUID adventureId) {
        return encounterRepository.findActive(adventureId)
                .flatMap(encounter -> operationRepository.findPendingMovementForEncounter(encounter.encounterId()))
                .map(CombatActionOperation::response)
                .filter(Objects::nonNull)
                .filter(response -> response.movementAdjudication() != null)
                .orElse(null);
    }

    private static int movementDistance(CombatActionCommand command) {
        if (command.movementPath() != null) {
            int pathDistance = CombatMovementPolicy.distanceOf(command.movementPath());
            if (command.movementDistance() != null && command.movementDistance() != pathDistance) {
                throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("MOVEMENT_DISTANCE_MISMATCH"));
            }
            return pathDistance;
        }
        if (command.movementDistance() != null) return command.movementDistance();
        throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("MOVEMENT_DISTANCE_REQUIRED"));
    }

    private static int traversedMovementDistance(CombatMapMoveResult result) {
        return Math.multiplyExact(Math.max(0, result.traversedPath().size() - 1), CombatMovementPolicy.GRID_DISTANCE_UNIT);
    }

    private static long expectedMapVersion(CombatActionCommand command) {
        return command.mapVersion() == null ? command.expectedVersion() : command.mapVersion();
    }

    private static void requireCommittedMovement(CombatMapMoveResult result) {
        switch (result.status()) {
            case COMMITTED, INTERRUPTED -> { }
            case CHECK_REQUIRED, RETRY_REQUIRED -> throw new CombatCommandRejectedException("RETRY_REQUIRED",
                    List.of("MOVEMENT_OPERATION_NOT_COMMITTED"));
            case CANCELLED -> throw new CombatCommandRejectedException("MOVEMENT_CANCELLED",
                    result.interruptionReason() == null ? List.of() : List.of(result.interruptionReason()));
        }
    }

    private static boolean requiresMovementAdjudication(CombatMapMoveResult movement) {
        return movementFollowUpTrigger(movement) != null;
    }

    private static String movementFollowUpTrigger(CombatMapMoveResult movement) {
        if (movement == null) return null;
        if (isMovementFollowUpTrigger(movement.interruptionReason())) return movement.interruptionReason();
        return movement.publicEvents().stream().filter(CombatActionApplicationService::isMovementFollowUpTrigger)
                .findFirst().orElse(null);
    }

    private static boolean isMovementFollowUpTrigger(String trigger) {
        return "HOSTILE_OBSERVED".equals(trigger) || "SPATIAL_FEATURE_REQUIRES_DECISION".equals(trigger);
    }

    private static void validateNarrativePosition(CombatActionCommand command, CombatEncounter encounter) {
        NarrativeCombatPosition position = command.narrativePosition();
        if (command.combatMapId() != null || position == null) return;
        if (!command.characterSheetId().value().equals(position.subjectId())) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("NARRATIVE_SUBJECT_MISMATCH"));
        }
        boolean targetExists = encounter.participants().stream()
                .anyMatch(participant -> participant.participantId().equals(position.targetId()));
        if (!targetExists || position.subjectId().equals(position.targetId())) {
            throw new CombatCommandRejectedException("ACTION_NOT_ALLOWED", List.of("NARRATIVE_TARGET_INVALID"));
        }
    }

    private static boolean stepDone(CombatActionOperation operation, String name) {
        return operation.steps().stream().anyMatch(step -> step.name().equals(name)
                && step.status() == CombatActionStep.Status.DONE);
    }

    private static UUID mapOperationId(CombatActionOperation operation) {
        CombatActionStep step = operation.step("map");
        if (step == null) return UUID.randomUUID();
        try {
            return UUID.fromString(step.idempotencyKey());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("durable map operation identity is invalid", invalid);
        }
    }

    private CombatActionResponse resumeCommittedMovementFollowUp(CombatActionCommand command,
            CombatActionOperation operation) {
        CombatActionResponse response = operation.response();
        if (response.movementAdjudication() != null || command.combatMapId() == null) {
            operation.committed(response);
            operationRepository.save(operation);
            return response;
        }
        CombatActionStep step = operation.step("map");
        if (step == null || step.status() != CombatActionStep.Status.DONE) return response;
        UUID mapOperationId = mapOperationId(operation);
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "combat_action_saved_map_recovery", "commandId=" + command.operationId() + " operationId="
                        + mapOperationId + " encounterId=" + operation.encounterId()
                        + " expected=saved_map_result actual=querying");
        try {
            CombatMapMoveResult movement = movementCoordinator.query(command.combatMapId(), mapOperationId);
            CombatEncounter encounter = encounterRepository.findByEncounterId(operation.encounterId())
                    .orElseThrow(() -> new IllegalStateException("combat encounter is unavailable for movement recovery"));
            CombatActionResponse recovered = attachMovementAdjudication(command, encounter, operation, movement, response);
            if (operation.status() == CombatActionOperation.Status.GM_INTERVENTION_PENDING) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "combat_action_saved_map_recovery", "commandId=" + command.operationId() + " operationId="
                                + mapOperationId + " encounterId=" + operation.encounterId()
                                + " expected=GM_INTERVENTION_PENDING actual=preserved recoveryAction=resume_same_operation", started);
                return recovered;
            }
            recovered = narrateAfterCommit(command, recovered, command.action(), encounter);
            operation.committed(recovered);
            operationRepository.save(operation);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "combat_action_saved_map_recovery", "commandId=" + command.operationId() + " operationId="
                            + mapOperationId + " encounterId=" + operation.encounterId()
                            + " expected=saved_map_result actual=replayed recoveryAction=reuse_saved_map_result", started);
            return recovered;
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "combat_action_saved_map_recovery", "commandId=" + command.operationId() + " operationId="
                            + mapOperationId + " encounterId=" + operation.encounterId()
                            + " expected=saved_map_result actual=failed recoveryAction=retry_same_operation", started, failure);
            throw failure;
        }
    }

    private void commitMovementResult(CombatEncounter committed, long expectedVersion,
            CombatActionOperation operation, int committedDistance) {
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "combat_action_movement_commit", "commandId=" + operation.commandId() + " operationId="
                        + operation.mapOperationId() + " encounterId=" + committed.encounterId()
                        + " expectedEncounterVersion=" + expectedVersion + " actualEncounterVersion="
                        + committed.version());
        Runnable writes = () -> {
            encounterRepository.save(committed, expectedVersion);
            operationRepository.save(operation);
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(),
                    "MOVEMENT_RESOLVED", "{\"operationId\":\"" + operation.commandId()
                    + "\",\"distance\":" + committedDistance + "}"));
        };
        try {
            if (movementCommitTransactions == null) {
                writes.run();
            } else {
                movementCommitTransactions.executeWithoutResult(status -> writes.run());
            }
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "combat_action_movement_commit", "commandId=" + operation.commandId() + " operationId="
                            + operation.mapOperationId() + " encounterId=" + committed.encounterId()
                            + " expectedEncounterVersion=" + expectedVersion + " actualEncounterVersion="
                            + committed.version() + " expected=encounter_operation_event_atomic actual=committed"
                            + " recoveryAction=replay_saved_operation", started);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "combat_action_movement_commit", "commandId=" + operation.commandId() + " operationId="
                            + operation.mapOperationId() + " encounterId=" + committed.encounterId()
                            + " expected=encounter_operation_event_atomic actual=failed"
                            + " recoveryAction=retry_same_operation", started, failure);
            throw failure;
        }
    }

    private CombatActionResponse attachMovementAdjudication(CombatActionCommand command, CombatEncounter encounter,
            CombatActionOperation operation, CombatMapMoveResult movement, CombatActionResponse response) {
        if (movement == null || movement.operationId() == null
                || !movement.operationId().equals(mapOperationId(operation))) {
            throw new IllegalStateException("movement recovery operation identity does not match stored action");
        }
        String trigger = movementFollowUpTrigger(movement);
        if (trigger == null) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(LOGGER,
                    "combat_action_unsupported_movement_trigger", "commandId=" + command.operationId()
                            + " operationId=" + mapOperationId(operation) + " encounterId=" + encounter.encounterId()
                            + " expected=HOSTILE_OBSERVED_or_SPATIAL_FEATURE_REQUIRES_DECISION actual=unsupported_or_absent"
                            + " interruptionReasonPresent=" + (movement.interruptionReason() != null)
                            + " publicEventCount=" + movement.publicEvents().size()
                            + " branch=keep_existing_response recoveryAction=return_saved_response",
                    () -> { });
            return response;
        }
        if (response.encounterVersion() != encounter.version()
                || !encounter.currentParticipantId().equals(operation.actorId())
                || !operation.actorId().equals(command.characterSheetId().value())) {
            throw new IllegalStateException("movement adjudication encounter version or actor changed");
        }
        if (movement.hostileTokenId() != null && !"HOSTILE_OBSERVED".equals(trigger)
                || movement.hostileTokenId() == null && "HOSTILE_OBSERVED".equals(trigger)) {
            throw new IllegalStateException("movement adjudication token identity does not match trigger");
        }
        if (movementFollowUpPort == null || movementFollowUpConsumer == null || runtimeCommands == null || objectMapper == null) {
            throw new IllegalStateException("movement adjudication recovery ports are not configured");
        }
        UUID turnId = UUID.nameUUIDFromBytes(("combat-turn:" + encounter.encounterId() + ":" + encounter.round()
                + ":" + encounter.currentParticipantId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var followUp = MovementFollowUpCommand.forTrigger(mapOperationId(operation), turnId,
                movement.hostileTokenId(), trigger, value -> MovementFollowUpCommand.Kind.COMBAT,
                movement.version(), movement.traversedPath(), movement.finalPosition(), movement.publicEvents());
        operation.awaitingMovementAdjudication(response, followUp);
        operationRepository.save(operation);
        UUID sourceId = UUID.nameUUIDFromBytes(("combat-action-movement-source:" + mapOperationId(operation))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sourceContext;
        try {
            sourceContext = objectMapper.writeValueAsString(java.util.Map.of("encounterId", encounter.encounterId(),
                    "actorId", operation.actorId(), "expectedEncounterVersion", command.expectedVersion(),
                    "operationId", mapOperationId(operation)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("movement adjudication source context serialization failed", failure);
        }
        var source = runtimeCommands.findByCommandId(sourceId).orElseGet(() -> {
            var created = com.dndmaster.adventure.application.runtime.RuntimeTurnCommand.create(turnId, sourceId,
                    command.adventureId().value(), command.sessionId(), command.ownerPlayerId(), sourceContext,
                    "combat-map.move", "{}", 0);
            runtimeCommands.save(created);
            return created;
        });
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "combat_action_movement_follow_up", "commandId=" + command.operationId() + " turnId=" + turnId
                        + " operationId=" + mapOperationId(operation) + " trigger=" + trigger
                        + " traversedCellCount=" + movement.traversedPath().size());
        try {
            var published = movementFollowUpPort.publish(followUp, command.adventureId().value(), command.sessionId(),
                    Objects.requireNonNull(command.ownerPlayerId(), "owner player id is required for movement recovery"));
            if (published.status() != com.dndmaster.adventure.application.runtime.MovementFollowUpPort.Result.Status.DONE) {
                throw new IllegalStateException("movement adjudication event could not be persisted: " + published.value());
            }
            var consumed = movementFollowUpConsumer.consume(source, followUp);
            if (consumed.status() != com.dndmaster.adventure.application.runtime.MovementFollowUpPort.Result.Status.DONE) {
                throw new IllegalStateException("movement adjudication did not complete: " + consumed.value());
            }
            final com.dndmaster.adventure.application.runtime.CombatContinuationCommand adjudication;
            try {
                adjudication = objectMapper.readValue(consumed.value(),
                        com.dndmaster.adventure.application.runtime.CombatContinuationCommand.class);
            } catch (java.io.IOException malformed) {
                throw new IllegalStateException("movement adjudication response is corrupt", malformed);
            }
            UUID continuationCommandId = UUID.nameUUIDFromBytes(("movement-continuation:" + followUp.commandId())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!continuationCommandId.equals(adjudication.commandId()) || !turnId.equals(adjudication.turnId())
                    || !followUp.operationId().equals(adjudication.operationId()) || !trigger.equals(adjudication.trigger())) {
                throw new IllegalStateException("movement adjudication response identity mismatch");
            }
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "combat_action_movement_follow_up", "commandId=" + command.operationId() + " turnId=" + turnId
                            + " operationId=" + mapOperationId(operation) + " expected=typed_gm_response actual=validated"
                            + " playerInputRequired=" + adjudication.playerInput().required()
                            + " waitingForPlayerInput=" + adjudication.waitingForPlayerInput()
                            + " combatStartRequested=" + adjudication.combatStartRequested()
                            + " recoveryAction=replay_saved_response", started);
            CombatActionResponse resolved = response.withMovementAdjudication(adjudication);
            if (adjudication.waitingForPlayerInput()) {
                operation.awaitingMovementAdjudication(resolved, followUp);
            } else {
                operation.committed(resolved);
            }
            operationRepository.save(operation);
            return resolved;
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "combat_action_movement_follow_up", "commandId=" + command.operationId() + " turnId=" + turnId
                            + " operationId=" + mapOperationId(operation) + " trigger=" + trigger
                            + " expected=typed_gm_response actual=failed recoveryAction=retry_same_operation", started, failure);
            throw failure;
        }
    }

    private static CombatActionCommand withOperationId(CombatActionCommand command, UUID operationId) {
        return new CombatActionCommand(operationId, command.adventureId(), command.sessionId(), command.ruleSetId(),
                command.characterSheetId(), command.combatMapId(), command.role(), command.action(), command.movementPath(),
                command.ownerPlayerId(), command.tokenId(), command.expectedVersion(), command.targetArmorClass(),
                command.attackModifier(), command.targetCharacterSheetId(), command.damageAmount(), command.endCombat(),
                command.narrativePosition(), command.movementDistance(), command.mapVersion(), command.aimSelection());
    }

    public CombatActionResponse endTurn(CombatActionCommand command) {
        return endTurn(command, com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.PLAYER);
    }

    public CombatActionResponse endTurnAi(CombatActionCommand command) {
        return endTurn(command, com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI);
    }

    private CombatActionResponse endTurn(CombatActionCommand command,
                                         com.dndmaster.adventure.domain.combat.CombatParticipant.Controller controller) {
        Objects.requireNonNull(command, "turn end command must not be null");
        CombatActionOperation existing = operationRepository.findByCommandId(command.operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
        }
        CombatEncounter encounter = activeEncounter(command);
        if (existing == null && operationRepository.hasPendingForEncounter(encounter.encounterId())) {
            long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                    "combat_action_pending_guard", "commandId=" + command.operationId() + " encounterId="
                            + encounter.encounterId() + " expected=no_pending_operation actual=pending_operation");
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "combat_action_pending_guard", "commandId=" + command.operationId() + " encounterId="
                            + encounter.encounterId() + " expected=allow_turn_end actual=retry_required"
                            + " recoveryAction=resume_same_pending_operation", started);
            throw new CombatCommandRejectedException("RETRY_REQUIRED", List.of("COMBAT_ACTION_RECOVERY_PENDING"));
        }
        if (!encounter.currentParticipantId().equals(command.characterSheetId().value())
                || encounter.currentParticipant().controller() != controller) {
            throw new CombatCommandRejectedException("COMBAT_STATE_REJECTED", List.of(
                    controller == com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.PLAYER
                            ? "NOT_PLAYER_TURN" : "NOT_AI_TURN"));
        }
        CharacterCombatStatus characterStatus = encounter.currentParticipant().statBlock() == null
                ? characterPort.combatStatus(command) : null;
        boolean deathSaveRequired = characterStatus != null && characterStatus.currentHitPoints() == 0
                && !characterStatus.stable() && !characterStatus.dead();
        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), command.fingerprint(), encounter.encounterId(),
                command.characterSheetId().value(), new TurnResourceCost(0, false, false, false), List.of(
                new CombatActionStep("death-save", command.operationId() + ":death-save", CombatActionStep.Status.PENDING),
                new CombatActionStep("turn-end", command.operationId() + ":turn-end", CombatActionStep.Status.PENDING)))
                : existing;
        operationRepository.save(operation);
        Integer deathSaveRoll = null;
        if (deathSaveRequired) {
            if (stepDone(operation, "death-save")) {
                deathSaveRoll = operation.diceTotal();
            } else {
                deathSaveRoll = dicePort.roll(command);
                operation.recordDiceTotal(deathSaveRoll);
                operationRepository.save(operation);
                characterPort.applyDeathSavingThrow(command, deathSaveRoll);
                operation.completeStep("death-save");
                operationRepository.save(operation);
            }
            characterStatus = characterPort.combatStatus(command);
            if (deathSaveRoll == 20) {
                CombatEncounter recorded = encounter.recordCurrentTurnEvent(command.expectedVersion());
                encounterRepository.save(recorded, encounter.version());
                eventRepository.append(new CombatEvent(recorded.encounterId(), recorded.eventCursor(),
                        "DEATH_SAVING_THROW", "{\"participantId\":\"" + command.characterSheetId().value()
                        + "\",\"roll\":20,\"result\":\"REGRAINS_HIT_POINT\"}"));
                CombatActionResponse response = new CombatActionResponse(recorded.encounterId(), command.operationId(),
                        recorded.version(), "DEATH_SAVE_REVIVED", deathSaveRoll, "natural 20; regained 1 hit point", List.of());
                operation.committed(response);
                operationRepository.save(operation);
                return response;
            }
        }
        String condition = characterStatus == null ? null : characterStatus.dead() ? "dead"
                : characterStatus.stable() ? "stable" : deathSaveRequired ? "unconscious" : null;
        CombatEncounter ended = encounter.endCurrentTurn(command.expectedVersion(), condition);
        encounterRepository.save(ended, encounter.version());
        String status = deathSaveRoll == null ? "TURN_ENDED"
                : deathSaveRoll >= 10 ? "DEATH_SAVE_SUCCESS" : "DEATH_SAVE_FAILURE";
        String result = deathSaveRoll == null ? "\"participantId\":\"" + command.characterSheetId().value() + "\""
                : "\"participantId\":\"" + command.characterSheetId().value() + "\",\"roll\":" + deathSaveRoll
                        + ",\"successes\":" + (characterStatus == null ? 0 : characterStatus.deathSavingThrowSuccesses())
                        + ",\"failures\":" + (characterStatus == null ? 0 : characterStatus.deathSavingThrowFailures())
                        + ",\"stable\":" + (characterStatus != null && characterStatus.stable())
                        + ",\"dead\":" + (characterStatus != null && characterStatus.dead());
        CombatActionResponse response = new CombatActionResponse(encounter.encounterId(), command.operationId(),
                ended.version(), status, deathSaveRoll, deathSaveRoll == null ? null : "death saving throw " + deathSaveRoll, List.of());
        operation.completeStep("turn-end");
        operation.committed(response);
        operationRepository.save(operation);
        eventRepository.append(new CombatEvent(ended.encounterId(), ended.eventCursor(), "TURN_ENDED", "{" + result + "}"));
        return response;
    }

    private CombatEncounter activeEncounter(CombatActionCommand command) {
        return encounterRepository.findActive(command.adventureId().value())
                .orElseThrow(() -> new CombatCommandRejectedException("COMBAT_NOT_ACTIVE", List.of("COMBAT_NOT_ACTIVE")));
    }

    private CombatActionResponse narrateAfterCommit(CombatActionCommand command, CombatActionResponse response,
                                                     String playerInput, CombatEncounter committedEncounter) {
        String narration = null;
        narration = defeatedTargetNarration(command, committedEncounter);
        if (narration == null) {
            try {
                String generated = narrationPort.narrate(CombatNarrationRequest.postResolution(command,
                        response.encounterVersion(), ConfirmedCombatState.from(committedEncounter), response.diceTotal(),
                        response.judgment(), playerInput, participantName(committedEncounter,
                                command.characterSheetId().value()), participantName(committedEncounter,
                                command.targetCharacterSheetId() == null ? null : command.targetCharacterSheetId().value())));
                if (generated != null && !generated.isBlank()) narration = generated;
            } catch (CombatNarrationPersistenceException exception) {
                throw exception;
            } catch (RuntimeException ignored) {
                // The canonical combat result is already committed; use only a Korean summary of that result.
            }
        }
        if (narration == null || narration.isBlank()) narration = safeResultNarration(command, response);
        try {
            long nextSequence = eventRepository.after(response.encounterId(), -1).stream()
                    .mapToLong(CombatEvent::sequence).max().orElse(0L) + 1;
            eventRepository.append(new CombatEvent(response.encounterId(), nextSequence, "GM_NARRATION",
                    "{\"operationId\":\"" + command.operationId() + "\",\"narration\":\""
                            + escape(narration) + "\"}"));
            return new CombatActionResponse(response.encounterId(), response.operationId(), response.encounterVersion(),
                    response.status(), response.diceTotal(), response.judgment(), response.violations(), narration,
                    response.movementAdjudication());
        } catch (RuntimeException ignored) {
            return new CombatActionResponse(response.encounterId(), response.operationId(), response.encounterVersion(),
                    response.status(), response.diceTotal(), response.judgment(), response.violations(), null,
                    response.movementAdjudication());
        }
    }

    private static String participantName(CombatEncounter encounter, java.util.UUID participantId) {
        if (participantId == null) return null;
        return encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(participantId))
                .map(com.dndmaster.adventure.domain.combat.CombatParticipant::displayName)
                .findFirst().orElse(null);
    }

    private static String defeatedTargetNarration(CombatActionCommand command, CombatEncounter encounter) {
        if (command.targetCharacterSheetId() == null) return null;
        var target = encounter.participants().stream()
                .filter(participant -> participant.participantId().equals(command.targetCharacterSheetId().value()))
                .filter(com.dndmaster.adventure.domain.combat.CombatParticipant::isDefeated)
                .findFirst().orElse(null);
        if (target == null) return null;
        boolean enemiesRemain = encounter.participants().stream()
                .anyMatch(participant -> participant.controller() == com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI
                        && participant.statBlock() != null && !participant.isDefeated());
        return "공격에 쓰러진 대상은 " + target.displayName() + "입니다. "
                + (enemiesRemain ? "다른 적들은 여전히 전투 중입니다. 다음 행동을 선택하세요."
                        : "모든 적이 쓰러져 전투가 끝났습니다.");
    }

    private static String safeResultNarration(CombatActionCommand command, CombatActionResponse response) {
        String action = command.action() == null ? "" : command.action().trim().toLowerCase(java.util.Locale.ROOT);
        String judgment = response.judgment() == null ? "" : response.judgment().trim().toLowerCase(java.util.Locale.ROOT);
        if (action.equals("attack") || (action.equals("ai_turn") && judgment.contains("attack="))) {
            return judgment.startsWith("hit") || judgment.startsWith("critical hit")
                    ? "공격이 적중했습니다. 전투 결과를 반영했습니다. 다음 행동을 선택하세요."
                    : judgment.startsWith("miss") || judgment.startsWith("critical miss")
                            ? "공격이 빗나갔습니다. 전투 결과를 반영했습니다. 다음 행동을 선택하세요."
                            : "공격 판정 결과를 반영했습니다. 다음 행동을 선택하세요.";
        }
        if (action.equals("cast_spell")) {
            return "주문 판정 결과를 전투에 반영했습니다. 다음 행동을 선택하세요.";
        }
        return "전투 행동의 결과를 반영했습니다. 다음 행동을 선택하세요.";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
