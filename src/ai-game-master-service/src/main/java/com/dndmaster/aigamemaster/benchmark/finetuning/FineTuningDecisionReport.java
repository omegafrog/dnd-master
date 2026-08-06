package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FineTuningDecisionReport(String schemaVersion, FineTuningDatasetSplit split,
                                       List<FineTuningEvaluation> evaluations, Decision decision, String rationale) {
    private static final String SCHEMA = "gm-quality-finetuning.v1";
    private static final double MIN_GAIN = .05;

    public enum Decision { GO, NO_GO }

    public FineTuningDecisionReport {
        if (!SCHEMA.equals(schemaVersion)) throw new IllegalArgumentException("unsupported fine-tuning schema");
        Objects.requireNonNull(split); evaluations = List.copyOf(Objects.requireNonNull(evaluations));
        Objects.requireNonNull(decision); rationale = required(rationale, "rationale");
        validateMatrix(split, evaluations);
        if (decision != decide(evaluations)) throw new IllegalArgumentException("decision does not match evaluation evidence");
    }

    public static FineTuningDecisionReport create(FineTuningDatasetSplit split, List<FineTuningEvaluation> evaluations) {
        Objects.requireNonNull(split); Objects.requireNonNull(evaluations);
        validateMatrix(split, evaluations);
        var first = evaluations.getFirst().configuration();
        if (evaluations.stream().anyMatch(e -> !sameSettings(first, e.configuration()))) throw new IllegalArgumentException("evaluation settings differ");
        Decision decision = decide(evaluations);
        double gain = average(evaluations, FineTuningModelArtifact.Variant.FINE_TUNED, FineTuningMetrics::qualityScore)
                - average(evaluations, FineTuningModelArtifact.Variant.BASE, FineTuningMetrics::qualityScore);
        String rationale = decision == Decision.GO
                ? "bottleneck=none; fine-tuned quality gain=" + gain + "; follow-up=monitor production metrics"
                : "bottleneck=quality-or-regression-gate; quality gain=" + gain + "; follow-up=improve data, prompting, or validation before rollout";
        return new FineTuningDecisionReport(SCHEMA, split, evaluations, decision, rationale);
    }

    private static void validateMatrix(FineTuningDatasetSplit split, List<FineTuningEvaluation> evaluations) {
        if (evaluations.size() != FineTuningModelArtifact.Variant.values().length * RagCondition.values().length) {
            throw new IllegalArgumentException("complete base/fine-tuned No/Current/Oracle matrix required");
        }
        Map<FineTuningModelArtifact.Variant, Map<RagCondition, FineTuningEvaluation>> matrix = new EnumMap<>(FineTuningModelArtifact.Variant.class);
        for (var variant : FineTuningModelArtifact.Variant.values()) matrix.put(variant, new EnumMap<>(RagCondition.class));
        for (var evaluation : evaluations) {
            if (!split.version().equals(evaluation.artifact().splitVersion()) || !split.trainingDigest().equals(evaluation.artifact().trainingDigest())) {
                throw new IllegalArgumentException("artifact split mismatch");
            }
            if (matrix.get(evaluation.artifact().variant()).put(evaluation.ragCondition(), evaluation) != null) throw new IllegalArgumentException("duplicate evaluation");
        }
        for (var values : matrix.values()) if (values.size() != RagCondition.values().length) throw new IllegalArgumentException("missing evaluation");
        var first = evaluations.getFirst().configuration();
        if (evaluations.stream().anyMatch(e -> !sameSettings(first, e.configuration()))) throw new IllegalArgumentException("evaluation settings differ");
    }

    private static Decision decide(List<FineTuningEvaluation> evaluations) {
        for (var condition : RagCondition.values()) {
            var base = find(evaluations, FineTuningModelArtifact.Variant.BASE, condition).metrics();
            var tuned = find(evaluations, FineTuningModelArtifact.Variant.FINE_TUNED, condition).metrics();
            double gain = tuned.qualityScore() - base.qualityScore();
            double standardError = Math.sqrt(tuned.qualityVariance() / tuned.sampleCount() + base.qualityVariance() / base.sampleCount());
            if (gain < MIN_GAIN || gain <= 1.96 * standardError || tuned.groundingRate() < base.groundingRate()
                    || tuned.koreanNarrationRate() < base.koreanNarrationRate() || tuned.structureSuccessRate() < base.structureSuccessRate()
                    || tuned.latencyMeanMs() > base.latencyMeanMs() || tuned.latencyVarianceMs() > base.latencyVarianceMs()
                    || tuned.costUsd() > base.costUsd()) return Decision.NO_GO;
        }
        return Decision.GO;
    }

    private static FineTuningEvaluation find(List<FineTuningEvaluation> evaluations, FineTuningModelArtifact.Variant variant, RagCondition condition) {
        return evaluations.stream().filter(e -> e.artifact().variant() == variant && e.ragCondition() == condition).findFirst().orElseThrow();
    }

    private static double average(List<FineTuningEvaluation> evaluations, FineTuningModelArtifact.Variant variant, java.util.function.ToDoubleFunction<FineTuningMetrics> metric) {
        return evaluations.stream().filter(e -> e.artifact().variant() == variant).mapToDouble(e -> metric.applyAsDouble(e.metrics())).average().orElseThrow();
    }

    private static boolean sameSettings(GmBenchmarkConfig a, GmBenchmarkConfig b) {
        return a.corpusVersion().equals(b.corpusVersion()) && a.temperature() == b.temperature()
                && a.tokenCap() == b.tokenCap() && a.contextSize() == b.contextSize() && a.repetitions() == b.repetitions();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
