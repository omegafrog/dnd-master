package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.PostCombatProjectionPolicy;
import java.util.Objects;

public record CombatEndResult(PostCombatProjectionPolicy.PostCombatSummary summary,
                              long encounterVersion) {
    public CombatEndResult {
        Objects.requireNonNull(summary);
        if (encounterVersion < 1) throw new IllegalArgumentException("encounter version must be positive");
    }
}
