package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.UUID;

public record CombatActionResponse(UUID encounterId, UUID operationId, long encounterVersion,
                                   String status, Integer diceTotal, String judgment,
                                   List<String> violations) {
    public CombatActionResponse {
        if (encounterId == null || operationId == null || status == null) throw new IllegalArgumentException("combat response identity is required");
        violations = List.copyOf(violations == null ? List.of() : violations);
    }
}
