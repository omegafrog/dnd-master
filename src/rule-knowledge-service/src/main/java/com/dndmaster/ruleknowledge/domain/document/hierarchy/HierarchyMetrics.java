package com.dndmaster.ruleknowledge.domain.document.hierarchy;

/** Deterministic quality counters for shadow/cutover gates. */
public record HierarchyMetrics(int sourceNodes, int confirmed, int tentative, int unresolved,
                               int cycles, int duplicateOwnership) {
    public static HierarchyMetrics from(CanonicalDocumentTree tree) {
        java.util.Set<String> sourceIds = tree.nodes().values().stream()
                .filter(node -> !node.synthetic()).map(CanonicalNode::id)
                .collect(java.util.stream.Collectors.toSet());
        int confirmed = 0, tentative = 0, unresolved = 0, duplicateOwnership = 0;
        java.util.Set<String> owned = new java.util.HashSet<>();
        for (HierarchyEdge edge : tree.edges()) {
            if (!sourceIds.contains(edge.childId())) continue;
            if (!owned.add(edge.childId())) duplicateOwnership++;
            switch (edge.status()) {
            case CONFIRMED -> confirmed++; case TENTATIVE -> tentative++; case UNRESOLVED -> unresolved++;
            }
        }
        return new HierarchyMetrics(sourceIds.size(), confirmed, tentative, unresolved,
                countCycles(tree, sourceIds), duplicateOwnership);
    }
    private static int countCycles(CanonicalDocumentTree tree, java.util.Set<String> sourceIds) {
        int cycles = 0;
        for (String start : sourceIds) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            String current = start;
            while (sourceIds.contains(current)) {
                if (!visited.add(current)) {
                    cycles++;
                    break;
                }
                HierarchyEdge edge = tree.edgeFor(current).orElse(null);
                if (edge == null || edge.status() == ResolutionStatus.UNRESOLVED) break;
                current = edge.parentId();
            }
        }
        return cycles;
    }
    public double preservationRatio() { return sourceNodes == 0 ? 1 : (double) (confirmed + tentative + unresolved) / sourceNodes; }
    public boolean validForCutover() { return preservationRatio() == 1 && cycles == 0 && duplicateOwnership == 0; }
}
