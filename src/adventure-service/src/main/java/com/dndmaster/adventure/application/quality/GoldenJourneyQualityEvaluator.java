package com.dndmaster.adventure.application.quality;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure evaluator shared by deterministic contract tests and release evidence producers. */
public final class GoldenJourneyQualityEvaluator {
    public GoldenJourneyQualityReport evaluate(List<GoldenJourneyTurn> turns, long latencyBudgetMillis) {
        Objects.requireNonNull(turns, "turns");
        if (turns.isEmpty()) throw new IllegalArgumentException("golden journey turns required");
        if (latencyBudgetMillis < 0) throw new IllegalArgumentException("latency budget must not be negative");

        int actionReflected = 0;
        int neutralFallback = 0;
        int providerMatches = 0;
        int overBudget = 0;
        int citationCount = 0;
        int exactCitations = 0;
        int relevantCitations = 0;
        long totalLatency = 0;
        List<Long> latencies = turns.stream().map(GoldenJourneyTurn::latencyMillis).sorted().toList();
        List<GoldenJourneyProviderAudit> audits = turns.stream()
                .map(turn -> new GoldenJourneyProviderAudit(
                        turn.requestedProvider(), turn.effectiveProvider(), turn.actualProvider()))
                .toList();
        for (int index = 0; index < turns.size(); index++) {
            GoldenJourneyTurn turn = turns.get(index);
            GoldenJourneyProviderAudit audit = audits.get(index);
            if (turn.actionReflected()) actionReflected++;
            if (turn.neutralFallback()) neutralFallback++;
            if (audit.requestedMatchesEffective() && audit.actualMatchesEffective()) providerMatches++;
            if (turn.latencyMillis() > latencyBudgetMillis) overBudget++;
            citationCount += turn.citationChunkIds().size();
            exactCitations += turn.citationExactCount();
            relevantCitations += turn.citationRelevantCount();
            totalLatency += turn.latencyMillis();
        }
        double denominator = turns.size();
        double citationDenominator = citationCount == 0 ? 1.0 : citationCount;
        return new GoldenJourneyQualityReport(
                turns.size(),
                turns.stream().map(GoldenJourneyTurn::playerInput).collect(Collectors.toSet()).size(),
                actionReflected / denominator,
                neutralFallback / denominator,
                exactCitations / citationDenominator,
                relevantCitations / citationDenominator,
                providerMatches / denominator,
                overBudget / denominator,
                totalLatency / denominator,
                percentile95(latencies),
                audits,
                latencyBudgetMillis);
    }

    private static double percentile95(List<Long> sorted) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * .95) - 1);
        return sorted.get(index);
    }
}
