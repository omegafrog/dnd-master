package com.dndmaster.ruleknowledge.domain.evidence;

import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNodeType;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalDocumentTree;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.ResolutionStatus;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RuleEvidenceProjector {
    /** Projects supplied canonical containment only; no parser hierarchy inference. */
    public RuleEvidenceProjection projectCanonical(RulebookId documentId, long extractionVersion,
                                                   NormalizedDocument document, CanonicalDocumentTree tree) {
        Map<String, UUID> ids = new java.util.LinkedHashMap<>();
        List<EvidenceUnit> units = new ArrayList<>();
        List<EvidenceEdge> edges = new ArrayList<>();
        for (NormalizedElement element : document.elements()) {
            if (element.text().isBlank()) continue;
            UUID id = UUID.nameUUIDFromBytes((document.sourceIdentity() + ":" + element.id()).getBytes(StandardCharsets.UTF_8));
            ids.put(element.id(), id);
            SourceSpan span = new SourceSpan(element.page(), element.sourceSpan().start() == null ? 0 : element.sourceSpan().start(),
                    element.sourceSpan().end() == null ? element.text().length() : element.sourceSpan().end(), element.text(),
                    "page:" + element.page() + ":" + element.id(), element.page(), element.sourceSpan().boundingBox(), element.order());
            units.add(new EvidenceUnit(id, documentId, extractionVersion, EvidenceKind.RULE, element.text(), EvidenceVisibility.PLAYER_VISIBLE, List.of(span)));
        }
        tree.edges().stream().filter(edge -> edge.status() == ResolutionStatus.CONFIRMED && !edge.parentId().isBlank())
                .filter(edge -> ids.containsKey(edge.childId()) && ids.containsKey(edge.parentId()))
                .forEach(edge -> {
                    NormalizedElement child = document.elements().stream().filter(element -> element.id().equals(edge.childId())).findFirst().orElseThrow();
                    SourceSpan span = new SourceSpan(child.page(), 0, child.text().length(), child.text(), "page:" + child.page() + ":" + child.id(), child.page(), child.sourceSpan().boundingBox(), child.order());
                    edges.add(new EvidenceEdge(ids.get(edge.childId()), ids.get(edge.parentId()), EvidenceEdgeType.PARENT, List.of(span)));
                });
        return new RuleEvidenceProjection(units, edges);
    }
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
            EvidenceVisibility visibility = node.type() == DocumentNodeType.UNKNOWN
                    ? EvidenceVisibility.UNKNOWN : EvidenceVisibility.PLAYER_VISIBLE;
            units.add(new EvidenceUnit(id, documentId, version, kind, node.text(), visibility, List.of(span)));
            if (parent != null) edges.add(new EvidenceEdge(id, parent, EvidenceEdgeType.PARENT, List.of(span)));
            current = id;
        }
        for (DocumentNode child : node.children()) visit(documentId, version, child, current, units, edges);
        return current;
    }
}
