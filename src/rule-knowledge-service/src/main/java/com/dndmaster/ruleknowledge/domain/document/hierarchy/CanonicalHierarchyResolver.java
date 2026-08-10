package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeleton;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonNode;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Milestone-2 fail-closed publication: anchors confirmed; every other leaf remains unresolved. */
public final class CanonicalHierarchyResolver {
    private final String resolverVersion;
    public CanonicalHierarchyResolver(String resolverVersion) { this.resolverVersion = resolverVersion; }
    public CanonicalDocumentTree resolve(NormalizedDocument document, AnchorSkeleton skeleton) {
        Map<String, CanonicalNode> nodes = new LinkedHashMap<>();
        for (NormalizedElement element : document.elements()) nodes.put(element.id(), new CanonicalNode(element.id(), element.parentId(), element.parserLevel(), element.sourceSpan(), false));
        nodes.put("UNRESOLVED", new CanonicalNode("UNRESOLVED", "", null, null, true));
        Map<String, AnchorSkeletonNode> anchors = skeleton.nodes().stream().collect(java.util.stream.Collectors.toMap(AnchorSkeletonNode::bodyElementId, node -> node));
        List<HierarchyEdge> edges = document.elements().stream().map(element -> edge(element, anchors.get(element.id()), anchors)).toList();
        CanonicalTreeValidator.validate(nodes, edges);
        return new CanonicalDocumentTree(nodes, edges, resolverVersion);
    }
    private HierarchyEdge edge(NormalizedElement element, AnchorSkeletonNode anchor, Map<String, AnchorSkeletonNode> anchors) {
        if (anchor != null) return new HierarchyEdge(element.id(), anchor.parentBodyElementId(), ResolutionStatus.CONFIRMED, anchor.confidence(), List.of(element.id()), resolverVersion);
        if (element.parentId() != null && anchors.containsKey(element.parentId()) && "HEADING".equalsIgnoreCase(element.type()) && !element.style().isBlank()) {
            return new HierarchyEdge(element.id(), element.parentId(), ResolutionStatus.TENTATIVE, 0.75,
                    List.of(element.id(), "parser-parent", "heading", "typography"), resolverVersion);
        }
        if (element.parentId() != null && anchors.containsKey(element.parentId())) return new HierarchyEdge(element.id(), element.parentId(), ResolutionStatus.TENTATIVE, 0.5,
                List.of(element.id(), "parser-parent"), resolverVersion);
        return new HierarchyEdge(element.id(), "UNRESOLVED", ResolutionStatus.UNRESOLVED, 0, List.of(element.id()), resolverVersion);
    }
}
