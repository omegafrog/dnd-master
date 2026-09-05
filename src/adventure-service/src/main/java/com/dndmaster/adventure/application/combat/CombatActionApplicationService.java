package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatActionEvaluation;
import com.dndmaster.adventure.domain.combat.CombatActionIntent;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.CombatRulesEngine;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.TurnResources;
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

    public CombatActionApplicationService(CombatEncounterRepository encounterRepository,
                                          CombatActionOperationRepository operationRepository,
                                          CombatEventRepository eventRepository,
                                          CombatRulesEngine rulesEngine, DiceCombatPort dicePort,
                                          CharacterCombatPort characterPort, AiCombatPort aiPort) {
        this.encounterRepository = Objects.requireNonNull(encounterRepository);
        this.operationRepository = Objects.requireNonNull(operationRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.rulesEngine = Objects.requireNonNull(rulesEngine);
        this.dicePort = Objects.requireNonNull(dicePort);
        this.characterPort = Objects.requireNonNull(characterPort);
        this.aiPort = Objects.requireNonNull(aiPort);
    }

    public CombatActionResponse submit(CombatActionCommand command) {
        Objects.requireNonNull(command, "combat command must not be null");
        CombatActionOperation existing = operationRepository.findByCommandId(command.operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
        }

        CombatEncounter encounter = activeEncounter(command);
        CombatActionEvaluation evaluation = rulesEngine.validateAction(encounter,
                new CombatActionIntent(command.characterSheetId().value(), command.action(), TurnResourceCost.actionOnly()));
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
            int diceTotal;
            if (operation.diceTotal() != null && operation.steps().stream().anyMatch(step -> step.name().equals("dice") && step.status() == CombatActionStep.Status.DONE)) {
                diceTotal = operation.diceTotal();
            } else {
                diceTotal = dicePort.roll(command);
                operation.recordDiceTotal(diceTotal);
                operation.completeStep("dice");
                operationRepository.save(operation);
            }
            CombatOutcome outcome = aiPort.adjudicateOutcome(command, diceTotal);
            if (!stepDone(operation, "character")) {
                characterPort.applyOutcome(command, outcome);
                operation.completeStep("character");
                operationRepository.save(operation);
            }
            CombatEncounter committed = encounter.commitAction(command.characterSheetId().value(), reservation);
            encounterRepository.save(committed, encounter.version());
            CombatActionResponse response = new CombatActionResponse(encounter.encounterId(), command.operationId(),
                    committed.version(), "COMMITTED", diceTotal, outcome.judgment(), List.of());
            operation.committed(response);
            operationRepository.save(operation);
            eventRepository.append(new CombatEvent(committed.encounterId(), committed.eventCursor(),
                    "ACTION_RESOLVED", "{\"operationId\":\"" + command.operationId()
                            + "\",\"diceTotal\":" + diceTotal + ",\"judgment\":\""
                            + escape(outcome.judgment()) + "\"}"));
            return response;
        } catch (RuntimeException exception) {
            operation.failed(exception);
            operationRepository.save(operation);
            throw new CombatExternalFailureException(exception);
        }
    }

    private static boolean stepDone(CombatActionOperation operation, String name) {
        return operation.steps().stream().anyMatch(step -> step.name().equals(name)
                && step.status() == CombatActionStep.Status.DONE);
    }

    public CombatActionResponse endTurn(CombatActionCommand command) {
        Objects.requireNonNull(command, "turn end command must not be null");
        CombatActionOperation existing = operationRepository.findByCommandId(command.operationId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return existing.response();
        }
        CombatEncounter encounter = activeEncounter(command);
        if (!encounter.currentParticipantId().equals(command.characterSheetId().value())
                || encounter.currentParticipant().controller() != com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.PLAYER) {
            throw new CombatCommandRejectedException("COMBAT_STATE_REJECTED", List.of("NOT_PLAYER_TURN"));
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
