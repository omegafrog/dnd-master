package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.UUID;

public record CombatActionResponse(UUID encounterId, UUID operationId, long encounterVersion,
                                   String status, Integer diceTotal, String judgment,
                                   List<String> violations, String narration,
                                   com.dndmaster.adventure.application.runtime.CombatContinuationCommand movementAdjudication) {
    public CombatActionResponse {
        if (encounterId == null || operationId == null || status == null) throw new IllegalArgumentException("combat response identity is required");
        violations = List.copyOf(violations == null ? List.of() : violations);
    }

    public CombatActionResponse(UUID encounterId, UUID operationId, long encounterVersion,
                                String status, Integer diceTotal, String judgment, List<String> violations) {
        this(encounterId, operationId, encounterVersion, status, diceTotal, judgment, violations, null, null);
    }
    public CombatActionResponse(UUID encounterId, UUID operationId, long encounterVersion,
                                String status, Integer diceTotal, String judgment, List<String> violations,
                                String narration) {
        this(encounterId, operationId, encounterVersion, status, diceTotal, judgment, violations, narration, null);
    }
    public CombatActionResponse withMovementAdjudication(
            com.dndmaster.adventure.application.runtime.CombatContinuationCommand adjudication) {
        return new CombatActionResponse(encounterId, operationId, encounterVersion, status, diceTotal,
                judgment, violations, narration, adjudication);
    }
}
