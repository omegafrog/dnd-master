package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;

public record CanonicalDocumentTree(Map<String, CanonicalNode> nodes, List<HierarchyEdge> edges, String resolverVersion) {
    public CanonicalDocumentTree {
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        if (resolverVersion == null || resolverVersion.isBlank()) throw new IllegalArgumentException("resolverVersion must not be blank");
    }
    public Optional<CanonicalNode> node(String id) { return Optional.ofNullable(nodes.get(id)); }
    public Optional<HierarchyEdge> edgeFor(String id) { return edges.stream().filter(edge -> edge.childId().equals(id)).findFirst(); }
    public List<String> semanticPath(String id) {
        HierarchyEdge edge = edgeFor(id).orElse(null);
        if (edge == null || edge.status() != ResolutionStatus.CONFIRMED || edge.parentId().equals("UNRESOLVED")) return List.of();
        if (edge.parentId().isBlank()) return List.of(id);
        return List.of(edge.parentId(), id);
    }
    public Optional<DerivedSourceSpan> derivedSpan(String id) {
        if (!nodes.containsKey(id)) return Optional.empty();
        List<CanonicalNode> covered = new ArrayList<>();
        for (CanonicalNode node : nodes.values()) if (!node.synthetic() && isConfirmedDescendant(node.id(), id)) covered.add(node);
        if (covered.isEmpty()) return Optional.empty();
        covered.sort(Comparator.comparingInt((CanonicalNode n) -> n.leafSpan().page()).thenComparingInt(n -> n.leafSpan().order()));
        CanonicalNode first = covered.get(0), last = covered.get(covered.size() - 1);
        return Optional.of(new DerivedSourceSpan(covered.stream().map(n -> n.leafSpan().sourceId()).toList(),
                first.leafSpan().page(), last.leafSpan().page(), first.leafSpan().order(), last.leafSpan().order()));
    }
    private boolean isConfirmedDescendant(String candidate, String ancestor) {
        String current = candidate;
        while (true) {
            if (current.equals(ancestor)) return true;
            HierarchyEdge edge = edgeFor(current).orElse(null);
            if (edge == null || edge.status() != ResolutionStatus.CONFIRMED || edge.parentId().isBlank() || edge.parentId().equals("UNRESOLVED")) return false;
            current = edge.parentId();
        }
    }
}
