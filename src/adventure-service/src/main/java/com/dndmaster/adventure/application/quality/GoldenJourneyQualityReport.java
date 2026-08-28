package com.dndmaster.adventure.application.quality;

import java.util.List;

/** Release-quality measurements for exactly the RAG-026 five-turn journey. */
public record GoldenJourneyQualityReport(
        int totalTurns,
        int distinctInputCount,
        double actionReflectionRate,
        double neutralFallbackRate,
        double citationExactness,
        double citationRelevance,
        double providerMatchRate,
        double latencyOverBudgetRate,
        double averageLatencyMillis,
        double p95LatencyMillis,
        List<GoldenJourneyProviderAudit> requestedEffectiveActualAudit,
        long latencyBudgetMillis) {
    public GoldenJourneyQualityReport {
        if (totalTurns < 1 || distinctInputCount < 0 || distinctInputCount > totalTurns) {
            throw new IllegalArgumentException("invalid golden journey counts");
        }
        requireRate(actionReflectionRate, "action reflection");
        requireRate(neutralFallbackRate, "neutral fallback");
        requireRate(citationExactness, "citation exactness");
        requireRate(citationRelevance, "citation relevance");
        requireRate(providerMatchRate, "provider match");
        requireRate(latencyOverBudgetRate, "latency over budget");
        if (!Double.isFinite(averageLatencyMillis) || averageLatencyMillis < 0
                || !Double.isFinite(p95LatencyMillis) || p95LatencyMillis < 0
                || latencyBudgetMillis < 0) {
            throw new IllegalArgumentException("invalid latency metrics");
        }
        requestedEffectiveActualAudit = List.copyOf(requestedEffectiveActualAudit);
        if (requestedEffectiveActualAudit.size() != totalTurns) {
            throw new IllegalArgumentException("provider audit must cover every turn");
        }
    }

    public boolean passed() {
        return totalTurns == 5
                && distinctInputCount == 5
                && actionReflectionRate == 1.0
                && neutralFallbackRate == 0.0
                && citationExactness == 1.0
                && citationRelevance == 1.0
                && providerMatchRate == 1.0
                && latencyOverBudgetRate == 0.0
                && requestedEffectiveActualAudit.stream().allMatch(GoldenJourneyProviderAudit::actualMatchesEffective);
    }

    private static void requireRate(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " rate must be between zero and one");
        }
    }
}
