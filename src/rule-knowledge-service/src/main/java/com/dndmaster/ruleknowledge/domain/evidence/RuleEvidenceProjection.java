package com.dndmaster.ruleknowledge.domain.evidence;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RuleEvidenceProjection(List<EvidenceUnit> units, List<EvidenceEdge> edges) {
    public RuleEvidenceProjection {
        units = List.copyOf(units);
        edges = List.copyOf(edges);
    }

    public EvidenceUnit unit(UUID id) {
        return units.stream().filter(unit -> unit.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("evidence not found: " + id));
    }

    public static RuleEvidenceProjection fixtureWithAncestorChain(int size) {
        RulebookId document = RulebookId.generate();
        List<EvidenceUnit> units = new ArrayList<>();
        List<EvidenceEdge> edges = new ArrayList<>();
        UUID parent = null;
        for (int i = 0; i < size; i++) {
            UUID id = UUID.randomUUID();
            SourceSpan span = new SourceSpan(i + 1, 0, 6, "rule " + i, "page:" + (i + 1));
            units.add(new EvidenceUnit(id, document, 1, EvidenceKind.RULE, "rule " + i,
                    EvidenceVisibility.PLAYER_VISIBLE, List.of(span)));
            if (parent != null) edges.add(new EvidenceEdge(id, parent, EvidenceEdgeType.PARENT, List.of(span)));
            parent = id;
        }
        return new RuleEvidenceProjection(units, edges);
    }
}
