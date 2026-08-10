package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CanonicalTreeValidator {
    private CanonicalTreeValidator() {}
    public static void validate(Map<String, CanonicalNode> nodes, List<HierarchyEdge> edges) {
        if (edges.size() != nodes.values().stream().filter(node -> !node.synthetic()).count()) throw new IllegalArgumentException("every source node requires one canonical edge");
        Map<String, String> parents = new HashMap<>();
        for (HierarchyEdge edge : edges) {
            if (!nodes.containsKey(edge.childId()) || parents.put(edge.childId(), edge.parentId()) != null) throw new IllegalArgumentException("duplicate or unknown canonical edge");
            if (!edge.parentId().isBlank() && !nodes.containsKey(edge.parentId())) throw new IllegalArgumentException("unknown canonical parent: " + edge.parentId());
        }
        for (String id : nodes.keySet()) {
            Set<String> seen = new HashSet<>(); String current = id;
            while (!current.equals("UNRESOLVED") && !current.isBlank()) {
                if (!seen.add(current)) throw new IllegalArgumentException("canonical cycle: " + id);
                current = parents.getOrDefault(current, "");
            }
        }
    }
}
