package com.dndmaster.combatmap.application.movement;

import java.util.UUID;

public record MovementCheckResultBody(UUID operationId, UUID checkId, Boolean success) {
}
