package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import com.dndmaster.aigamemaster.benchmark.rag.CurrentRagEvidenceProvider;
import com.dndmaster.aigamemaster.benchmark.rag.DistractorRagEvidenceProvider;
import com.dndmaster.aigamemaster.benchmark.rag.NoRagEvidenceProvider;
import com.dndmaster.aigamemaster.benchmark.rag.OracleRagEvidenceProvider;
import com.dndmaster.aigamemaster.benchmark.rag.RagAbCase;
import com.dndmaster.aigamemaster.benchmark.rag.RagEvidenceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runs both provider identities against the same split and generation settings. */
public final class FineTuningEvaluationRunner {
    public FineTuningDecisionReport run(FineTuningDatasetSplit split, RagAbCase benchmarkCase,
                                        RagEvidenceProvider currentRag, FineTuningModelArtifact base,
                                        FineTuningModelArtifact fineTuned, GmBenchmarkConfig configuration,
                                        FineTuningEvaluationExecutor executor) {
        return runInternal(split, List.of(Objects.requireNonNull(benchmarkCase)), currentRag, base, fineTuned,
                configuration, executor, true);
    }

    /** Runs a complete frozen holdout corpus and aggregates each artifact/condition cell. */
    public FineTuningDecisionReport run(FineTuningDatasetSplit split, List<RagAbCase> holdoutCases,
                                        RagEvidenceProvider currentRag, FineTuningModelArtifact base,
                                        FineTuningModelArtifact fineTuned, GmBenchmarkConfig configuration,
                                        FineTuningEvaluationExecutor executor) {
        return runInternal(split, holdoutCases, currentRag, base, fineTuned, configuration, executor, true);
    }

    private FineTuningDecisionReport runInternal(FineTuningDatasetSplit split, List<RagAbCase> holdoutCases,
                                        RagEvidenceProvider currentRag, FineTuningModelArtifact base,
                                        FineTuningModelArtifact fineTuned, GmBenchmarkConfig configuration,
                                        FineTuningEvaluationExecutor executor, boolean validateHoldout) {
        Objects.requireNonNull(split); Objects.requireNonNull(currentRag);
        Objects.requireNonNull(base); Objects.requireNonNull(fineTuned);
        Objects.requireNonNull(configuration); Objects.requireNonNull(executor);
        holdoutCases = List.copyOf(Objects.requireNonNull(holdoutCases));
        if (holdoutCases.isEmpty()) throw new IllegalArgumentException("frozen holdout cases required");
        var ids = holdoutCases.stream().map(RagAbCase::id).toList();
        if (ids.stream().distinct().count() != ids.size()) throw new IllegalArgumentException("holdout case ids must be unique");
        if (validateHoldout && (!new java.util.HashSet<>(ids).equals(new java.util.HashSet<>(split.holdoutCaseIds()))
                || ids.size() != split.holdoutCaseIds().size())) {
            throw new IllegalArgumentException("holdout cases do not match frozen split");
        }
        if (base.variant() != FineTuningModelArtifact.Variant.BASE || fineTuned.variant() != FineTuningModelArtifact.Variant.FINE_TUNED) {
            throw new IllegalArgumentException("base and fine-tuned artifacts required");
        }
        List<FineTuningEvaluation> evaluations = new ArrayList<>();
        for (var artifact : List.of(base, fineTuned)) for (var condition : RagCondition.values()) {
            List<FineTuningMetrics> metrics = new ArrayList<>();
            for (var benchmarkCase : holdoutCases) {
                List<String> evidence = switch (condition) {
                    case NO_RAG -> new NoRagEvidenceProvider().evidence(benchmarkCase.source());
                    case CURRENT_RAG -> new CurrentRagEvidenceProvider(currentRag).evidence(benchmarkCase.source());
                    case ORACLE -> new OracleRagEvidenceProvider().evidence(benchmarkCase.source());
                    case DISTRACTOR -> new DistractorRagEvidenceProvider().evidence(benchmarkCase);
                };
                try {
                    metrics.add(Objects.requireNonNull(executor.evaluate(artifact, condition, split, configuration, evidence)));
                } catch (RuntimeException failure) {
                    throw new IllegalArgumentException("incomplete fine-tuning evaluation matrix", failure);
                }
            }
            evaluations.add(new FineTuningEvaluation(artifact, condition, configuration, aggregate(metrics)));
        }
        var backlinks = evaluations.stream()
                .map(e -> "raw/" + e.artifact().variant().name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + e.ragCondition().name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + e.artifact().artifactDigest() + ".json")
                .toList();
        return FineTuningDecisionReport.create(split, evaluations, backlinks);
    }

    private static FineTuningMetrics aggregate(List<FineTuningMetrics> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("holdout metrics required");
        int samples = values.stream().mapToInt(FineTuningMetrics::sampleCount).sum();
        var qualitySamples = values.stream().flatMap(value -> value.qualitySamples().stream()).toList();
        return new FineTuningMetrics(
                weighted(values, FineTuningMetrics::qualityScore, samples),
                weighted(values, FineTuningMetrics::groundingRate, samples),
                weighted(values, FineTuningMetrics::koreanNarrationRate, samples),
                weighted(values, FineTuningMetrics::structureSuccessRate, samples),
                weighted(values, FineTuningMetrics::latencyMeanMs, samples),
                pooledVariance(values, FineTuningMetrics::latencyMeanMs, FineTuningMetrics::latencyVarianceMs, samples),
                weighted(values, FineTuningMetrics::costUsd, samples),
                pooledVariance(values, FineTuningMetrics::qualityScore, FineTuningMetrics::qualityVariance, samples),
                samples,
                weighted(values, FineTuningMetrics::secretLeakRate, samples),
                weighted(values, FineTuningMetrics::stateConsistencyRate, samples),
                weighted(values, FineTuningMetrics::scopeComplianceRate, samples),
                qualitySamples);
    }

    private static double weighted(List<FineTuningMetrics> values,
                                   java.util.function.ToDoubleFunction<FineTuningMetrics> metric, int samples) {
        return values.stream().mapToDouble(value -> metric.applyAsDouble(value) * value.sampleCount()).sum() / samples;
    }

    private static double pooledVariance(List<FineTuningMetrics> values,
                                        java.util.function.ToDoubleFunction<FineTuningMetrics> means,
                                        java.util.function.ToDoubleFunction<FineTuningMetrics> variances, int samples) {
        double mean = weighted(values, means, samples);
        return values.stream().mapToDouble(value -> value.sampleCount() * variances.applyAsDouble(value)
                + value.sampleCount() * Math.pow(means.applyAsDouble(value) - mean, 2)).sum() / samples;
    }
}
