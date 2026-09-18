package com.dndmaster.combatmap.application.movement;

import java.util.UUID;

public record MovementOperationResponse(UUID operationId, MovementOperationStatus status,
        MovementResolutionResult result) { }
