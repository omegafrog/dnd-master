package com.dndmaster.ruleknowledge.domain.evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AncestorExpansionPolicy {
    private final int minimumBudget;

    public AncestorExpansionPolicy(int minimumBudget) {
        if (minimumBudget <= 0) throw new IllegalArgumentException("minimumBudget must be positive");
        this.minimumBudget = minimumBudget;
    }

    public List<EvidenceUnit> expand(UUID leafId, RuleEvidenceProjection projection, int budget) {
        if (budget < minimumBudget) throw new IllegalArgumentException("budget below policy minimum");
        List<EvidenceUnit> result = new ArrayList<>();
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        UUID current = leafId;
        int used = 0;
        while (current != null) {
            if (!visited.add(current)) break;
            EvidenceUnit unit = projection.unit(current);
            if (used + unit.tokenCount() > budget) break;
            result.add(unit);
            used += unit.tokenCount();
            UUID child = current;
            current = projection.edges().stream()
                    .filter(edge -> edge.from().equals(child) && edge.type() == EvidenceEdgeType.PARENT)
                    .map(EvidenceEdge::to).findFirst().orElse(null);
        }
        return List.copyOf(result);
    }
}
