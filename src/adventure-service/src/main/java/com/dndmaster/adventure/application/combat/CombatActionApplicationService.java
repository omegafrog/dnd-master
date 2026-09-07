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
    private final CombatMapPort mapPort;
    private final CombatEndPort combatEndPort;

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
        this.encounterRepository = Objects.requireNonNull(encounterRepository);
        this.operationRepository = Objects.requireNonNull(operationRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.rulesEngine = Objects.requireNonNull(rulesEngine);
        this.dicePort = Objects.requireNonNull(dicePort);
        this.characterPort = Objects.requireNonNull(characterPort);
        this.aiPort = Objects.requireNonNull(aiPort);
        this.mapPort = Objects.requireNonNull(mapPort);
        this.decisionPort = Objects.requireNonNull(decisionPort);
        this.combatEndPort = Objects.requireNonNull(combatEndPort);
    }

    public CombatActionResponse submitFreeForm(FreeFormCombatCommand command) {
        Objects.requireNonNull(command, "free-form combat command must not be null");
        CombatActionOperation existing = operationRepository.findByCommandId(command.action().operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
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
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor() + 1, "GM_NARRATION",
                    "{\"operationId\":\"" + command.action().operationId() + "\",\"narration\":\""
                            + escape(plan.narration()) + "\"}"));
            if (committed.allEnemiesDefeated()) {
                CombatEndResult ended = combatEndPort.endWhenEnemiesDefeated(command.action().adventureId().value());
                response = new CombatActionResponse(committed.encounterId(), command.action().operationId(),
                        ended.encounterVersion(), "COMBAT_ENDED", plan.requiresRoll() ? diceTotal : null,
                        plan.judgment(), List.of(), plan.narration());
                operation.committed(response);
                operationRepository.save(operation);
            }
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

    private static List<CombatActionStep> freeFormSteps(UUID operationId, FreeFormActionPlan plan) {
        List<CombatActionStep> steps = new java.util.ArrayList<>();
        if (plan.requiresRoll()) steps.add(new CombatActionStep("dice", operationId + ":dice", CombatActionStep.Status.PENDING));
        if (plan.effects().mapEffect() != null) steps.add(new CombatActionStep("map", operationId + ":map", CombatActionStep.Status.PENDING));
        steps.add(new CombatActionStep("character", operationId + ":character", CombatActionStep.Status.PENDING));
        return steps;
    }

    private void applyMapEffect(CombatActionOperation operation, CombatActionCommand command, CombatMapEffect effect) {
        if (effect == null || stepDone(operation, "map")) return;
        mapPort.move(new CombatMapMoveCommand(command, effect.movementDistance(), effect.expectedVersion()));
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
            characterPort.requireUsableCharacter(command);
            CombatActionCommand resolvedCommand = resolvePlayerAttack(command, encounter);
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
                mapPort.move(new CombatMapMoveCommand(command, distance, expectedMapVersion(command)));
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
        CombatEncounter ended = encounter.endCurrentTurn(command.expectedVersion());
        encounterRepository.save(ended, encounter.version());
        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.operationId(), command.fingerprint(), encounter.encounterId(),
                command.characterSheetId().value(), new TurnResourceCost(0, false, false, false), List.of(
                new CombatActionStep("turn-end", command.operationId() + ":turn-end", CombatActionStep.Status.DONE)))
                : existing;
        CombatActionResponse response = new CombatActionResponse(encounter.encounterId(), command.operationId(),
                ended.version(), "TURN_ENDED", null, null, List.of());
        operation.committed(response);
        operationRepository.save(operation);
        eventRepository.append(new CombatEvent(ended.encounterId(), ended.eventCursor(), "TURN_ENDED",
                "{\"participantId\":\"" + command.characterSheetId().value() + "\"}"));
        return response;
    }

    private CombatEncounter activeEncounter(CombatActionCommand command) {
        return encounterRepository.findActive(command.adventureId().value())
                .orElseThrow(() -> new CombatCommandRejectedException("COMBAT_NOT_ACTIVE", List.of("COMBAT_NOT_ACTIVE")));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
