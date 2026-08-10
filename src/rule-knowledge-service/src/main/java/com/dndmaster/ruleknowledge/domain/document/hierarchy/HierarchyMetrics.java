package com.dndmaster.ruleknowledge.domain.document.hierarchy;

/** Deterministic quality counters for shadow/cutover gates. */
public record HierarchyMetrics(int sourceNodes, int confirmed, int tentative, int unresolved,
                               int cycles, int duplicateOwnership) {
    public static HierarchyMetrics from(CanonicalDocumentTree tree) {
        int confirmed = 0, tentative = 0, unresolved = 0;
        for (HierarchyEdge edge : tree.edges()) switch (edge.status()) {
            case CONFIRMED -> confirmed++; case TENTATIVE -> tentative++; case UNRESOLVED -> unresolved++;
        }
        return new HierarchyMetrics((int) tree.nodes().values().stream().filter(node -> !node.synthetic()).count(), confirmed, tentative, unresolved, 0, 0);
    }
    public double preservationRatio() { return sourceNodes == 0 ? 1 : (double) (confirmed + tentative + unresolved) / sourceNodes; }
    public boolean validForCutover() { return preservationRatio() == 1 && cycles == 0 && duplicateOwnership == 0; }
}
