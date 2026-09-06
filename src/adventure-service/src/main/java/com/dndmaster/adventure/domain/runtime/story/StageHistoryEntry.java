package com.dndmaster.adventure.domain.runtime.story;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable execution snapshot retained after a stage leaves the current slot. */
public record StageHistoryEntry(String stageId, long detailedStageRevision, StageLifecycle lifecycle,
                                Set<String> usedSituationIds, Set<String> learnedRevelationIds,
                                List<String> unresolvedThreats, List<String> unresolvedConsequenceIds,
                                String exitReason) {
    public StageHistoryEntry {
        if (stageId == null || stageId.isBlank()) throw new IllegalArgumentException("stage id is required");
        if (detailedStageRevision < 1) throw new IllegalArgumentException("stage revision must be positive");
        lifecycle = Objects.requireNonNull(lifecycle, "stage lifecycle must not be null");
        usedSituationIds = Set.copyOf(usedSituationIds == null ? Set.of() : usedSituationIds);
        learnedRevelationIds = Set.copyOf(learnedRevelationIds == null ? Set.of() : learnedRevelationIds);
        unresolvedThreats = List.copyOf(unresolvedThreats == null ? List.of() : unresolvedThreats);
        unresolvedConsequenceIds = List.copyOf(unresolvedConsequenceIds == null ? List.of() : unresolvedConsequenceIds);
        exitReason = exitReason == null || exitReason.isBlank() ? null : exitReason.trim();
    }
}
