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
                damage = dicePort.rollDamage(3, 4, 3);
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
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
        }

        CombatEncounter encounter = activeEncounter(command);
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

        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), command.fingerprint(), encounter.encounterId(),
                command.characterSheetId().value(), evaluation.cost(), List.of(
                new CombatActionStep("dice", command.operationId() + ":dice", CombatActionStep.Status.PENDING),
                new CombatActionStep("character", command.operationId() + ":character", CombatActionStep.Status.PENDING)))
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
                    resolvedCommand.targetCharacterSheetId() == null ? null : resolvedCommand.targetCharacterSheetId().value(), damage);
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
                command.movementDistance(), command.mapVersion());
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

        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), command.fingerprint(), encounter.encounterId(),
                command.characterSheetId().value(), evaluation.cost(), List.of(
                new CombatActionStep(command.combatMapId() == null ? "narrative" : "map",
                        command.operationId() + (command.combatMapId() == null ? ":narrative" : ":map"),
                        CombatActionStep.Status.PENDING)))
                : existing;
        operationRepository.save(operation);
        try {
            if (command.combatMapId() != null && !stepDone(operation, "map")) {
                requireCommittedMovement(movementCoordinator.resolve(
                        new CombatMapMoveCommand(command, distance, expectedMapVersion(command))));
                operation.completeStep("map");
                operationRepository.save(operation);
            } else if (command.combatMapId() == null) {
                operation.completeStep("narrative");
                operationRepository.save(operation);
            }
            CombatEncounter committed = encounter.commitMovement(command.characterSheetId().value(), reservation,
                    command.narrativePosition());
            encounterRepository.save(committed, encounter.version());
            String judgment = command.narrativePosition() == null
                    ? "moved " + distance + "ft" : command.narrativePosition().rangeBand()
                    + " range, " + command.narrativePosition().cover() + " cover";
            CombatActionResponse response = new CombatActionResponse(committed.encounterId(), command.operationId(),
                    committed.version(), "MOVE_COMMITTED", null, judgment, List.of());
            operation.committed(response);
            operationRepository.save(operation);
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(),
                    "MOVEMENT_RESOLVED", "{\"operationId\":\"" + command.operationId()
                    + "\",\"distance\":" + distance + "}"));
            response = narrateAfterCommit(command, response, command.action(), committed);
            operation.committed(response);
            operationRepository.save(operation);
            return response;
        } catch (CombatCommandRejectedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw new CombatExternalFailureException(exception);
        }
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
                    response.status(), response.diceTotal(), response.judgment(), response.violations(), narration);
        } catch (RuntimeException ignored) {
            return new CombatActionResponse(response.encounterId(), response.operationId(), response.encounterVersion(),
                    response.status(), response.diceTotal(), response.judgment(), response.violations(), null);
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
