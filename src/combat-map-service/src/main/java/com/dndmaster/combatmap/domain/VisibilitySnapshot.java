package com.dndmaster.combatmap.domain;

import java.util.List;
import java.util.Set;

public record VisibilitySnapshot(Set<GridPosition> current, Set<GridPosition> explored,
        Set<TokenId> observedTokens, List<LastSeenState> lastSeen, long ruleTurn) {
    public VisibilitySnapshot {
        current = Set.copyOf(current);
        explored = Set.copyOf(explored);
        observedTokens = Set.copyOf(observedTokens);
        lastSeen = List.copyOf(lastSeen);
        if (!explored.containsAll(current) || ruleTurn < 0) {
            throw new IllegalArgumentException("visibility snapshot is inconsistent");
        }
    }
}
