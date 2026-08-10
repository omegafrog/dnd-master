package com.dndmaster.ruleknowledge.domain.evidence;

import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNodeType;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RuleEvidenceProjector {
    public RuleEvidenceProjection project(RulebookId documentId, long extractionVersion, DocumentNode root) {
        List<EvidenceUnit> units = new ArrayList<>();
        List<EvidenceEdge> edges = new ArrayList<>();
        visit(documentId, extractionVersion, root, null, units, edges);
        return new RuleEvidenceProjection(units, edges);
    }

    private UUID visit(RulebookId documentId, long version, DocumentNode node, UUID parent,
            List<EvidenceUnit> units, List<EvidenceEdge> edges) {
        UUID current = parent;
        if (!node.text().isBlank() && node.type() != DocumentNodeType.ROOT) {
            UUID id = UUID.randomUUID();
            SourceSpan span = new SourceSpan(1, 0, node.text().length(), node.text(),
                    "page:" + node.page() + ":" + node.id(), node.page(), node.boundingBox(), units.size());
            EvidenceKind kind = node.type() == DocumentNodeType.UNKNOWN ? EvidenceKind.RAW : EvidenceKind.RULE;
            units.add(new EvidenceUnit(id, documentId, version, kind, node.text(),
                    EvidenceVisibility.PLAYER_VISIBLE, List.of(span)));
            if (parent != null) edges.add(new EvidenceEdge(id, parent, EvidenceEdgeType.PARENT, List.of(span)));
            current = id;
        }
        for (DocumentNode child : node.children()) visit(documentId, version, child, current, units, edges);
        return current;
    }
}
