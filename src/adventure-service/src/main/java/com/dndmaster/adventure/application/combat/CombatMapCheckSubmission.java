package com.dndmaster.adventure.application.combat;

import java.util.UUID;

public record CombatMapCheckSubmission(UUID operationId, UUID checkId, boolean success) {
}
