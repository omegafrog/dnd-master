package com.dndmaster.ruleknowledge.domain.evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AncestorExpansionPolicy {
    public AncestorExpansionPolicy(int minimumBudget) {
        if (minimumBudget <= 0) throw new IllegalArgumentException("minimumBudget must be positive");
    }

    public List<EvidenceUnit> expand(UUID leafId, RuleEvidenceProjection projection, int budget) {
        if (budget <= 0) throw new IllegalArgumentException("budget must be positive");
        List<EvidenceUnit> result = new ArrayList<>();
        UUID current = leafId;
        int used = 0;
        while (current != null) {
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
