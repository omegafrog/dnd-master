package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

/** Reviewable AI proposal; it cannot become a party member before explicit adoption. */
public record AiCompanionCandidate(UUID candidateId, String name, String race, String characterClass,
                                   String sheetSummary) {
    public AiCompanionCandidate {
        Objects.requireNonNull(candidateId, "candidate id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(race, "race must not be null");
        Objects.requireNonNull(characterClass, "class must not be null");
        Objects.requireNonNull(sheetSummary, "sheet summary must not be null");
    }
}
