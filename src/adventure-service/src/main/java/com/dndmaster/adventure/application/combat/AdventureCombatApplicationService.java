package com.dndmaster.adventure.application.combat;

import java.util.Objects;

public final class AdventureCombatApplicationService {
    private final CombatOperationRepository repository;
    private final CharacterCombatPort characterPort;
    private final DiceCombatPort dicePort;
    private final CombatMapPort mapPort;
    private final AiCombatPort aiPort;

    public AdventureCombatApplicationService(
            CombatOperationRepository repository, CharacterCombatPort characterPort, DiceCombatPort dicePort,
            CombatMapPort mapPort, AiCombatPort aiPort) {
        this.repository = Objects.requireNonNull(repository);
        this.characterPort = Objects.requireNonNull(characterPort);
        this.dicePort = Objects.requireNonNull(dicePort);
        this.mapPort = Objects.requireNonNull(mapPort);
        this.aiPort = Objects.requireNonNull(aiPort);
    }

    public CombatResult resolveCombatAction(CombatActionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CombatOperation operation = repository.findById(command.operationId()).orElse(null);
        if (operation == null) {
            operation = new CombatOperation(command.operationId(), command.fingerprint());
            repository.save(operation);
        }
        operation.requireSame(command.fingerprint());
        if (operation.judgment().isPresent()) return result(command, operation);

        if (!operation.isCharacterVerified()) {
            characterPort.requireUsableCharacter(command);
            operation.characterVerified();
            repository.save(operation);
        }
        if (operation.diceTotal().isEmpty()) {
            operation.diceRolled(dicePort.roll(command));
            repository.save(operation);
        }
        if (command.movementPath() != null && !operation.isMovementCompleted()) {
            mapPort.validateAndMove(command);
            operation.movementCompleted();
            repository.save(operation);
        }
        if (!operation.isAiStateControlled()) {
            aiPort.controlState(command);
            operation.aiStateControlled();
            repository.save(operation);
        }
        if (operation.judgment().isEmpty()) {
            operation.adjudicated(aiPort.adjudicate(command, operation.diceTotal().orElseThrow()));
            repository.save(operation);
        }
        return result(command, operation);
    }

    private static CombatResult result(CombatActionCommand command, CombatOperation operation) {
        return new CombatResult(command.operationId(), command.role(), operation.diceTotal().orElseThrow(),
                operation.judgment().orElseThrow());
    }
}
