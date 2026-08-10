package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeleton;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonNode;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.LinkedHashMap;
import java.util.Comparator;
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
        List<NormalizedElement> ordered = document.elements().stream()
                .sorted(Comparator.comparingInt(NormalizedElement::page).thenComparingInt(NormalizedElement::order)).toList();
        List<HierarchyEdge> edges = ordered.stream().map(element -> edge(element, anchors.get(element.id()), anchors, ordered)).toList();
        CanonicalTreeValidator.validate(nodes, edges);
        return new CanonicalDocumentTree(nodes, edges, resolverVersion);
    }
    private HierarchyEdge edge(NormalizedElement element, AnchorSkeletonNode anchor, Map<String, AnchorSkeletonNode> anchors,
                               List<NormalizedElement> ordered) {
        if (anchor != null) return new HierarchyEdge(element.id(), anchor.parentBodyElementId(), ResolutionStatus.CONFIRMED, anchor.confidence(), List.of(element.id()), resolverVersion);
        NormalizedElement tocSection = precedingAnchor(element, anchors, ordered);
        if (tocSection != null) return new HierarchyEdge(element.id(), tocSection.id(), ResolutionStatus.CONFIRMED, 0.9,
                List.of(element.id(), tocSection.id(), "toc-range"), resolverVersion);
        if (element.parentId() != null && anchors.containsKey(element.parentId()) && "HEADING".equalsIgnoreCase(element.type()) && !element.style().isBlank()) {
            return new HierarchyEdge(element.id(), element.parentId(), ResolutionStatus.TENTATIVE, 0.75,
                    List.of(element.id(), "parser-parent", "heading", "typography"), resolverVersion);
        }
        if (element.parentId() != null && anchors.containsKey(element.parentId())) return new HierarchyEdge(element.id(), element.parentId(), ResolutionStatus.TENTATIVE, 0.5,
                List.of(element.id(), "parser-parent"), resolverVersion);
        return new HierarchyEdge(element.id(), "UNRESOLVED", ResolutionStatus.UNRESOLVED, 0, List.of(element.id()), resolverVersion);
    }

    private NormalizedElement precedingAnchor(NormalizedElement element, Map<String, AnchorSkeletonNode> anchors,
                                              List<NormalizedElement> ordered) {
        NormalizedElement result = null;
        for (NormalizedElement candidate : ordered) {
            if (candidate.page() > element.page() || candidate.page() == element.page() && candidate.order() >= element.order()) break;
            if (anchors.containsKey(candidate.id())) result = candidate;
        }
        return result;
    }
}
