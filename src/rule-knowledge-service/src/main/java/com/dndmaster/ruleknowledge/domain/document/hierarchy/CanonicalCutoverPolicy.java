package com.dndmaster.ruleknowledge.domain.document.hierarchy;

/** Explicit feature-flag gate; default stays legacy/shadow-only. */
public record CanonicalCutoverPolicy(boolean enabled, double minimumConfirmedRatio) {
    public CanonicalCutoverPolicy {
        if (!Double.isFinite(minimumConfirmedRatio) || minimumConfirmedRatio < 0 || minimumConfirmedRatio > 1) throw new IllegalArgumentException("invalid confirmed ratio");
    }
    public boolean permits(HierarchyMetrics metrics) {
        return enabled && metrics.validForCutover() && metrics.sourceNodes() > 0
                && (double) metrics.confirmed() / metrics.sourceNodes() >= minimumConfirmedRatio;
    }
    public static CanonicalCutoverPolicy shadowOnly() { return new CanonicalCutoverPolicy(false, 1); }
}
