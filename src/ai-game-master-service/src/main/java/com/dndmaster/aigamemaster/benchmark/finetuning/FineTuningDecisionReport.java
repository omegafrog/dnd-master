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
    }

    public static FineTuningDecisionReport create(FineTuningDatasetSplit split, List<FineTuningEvaluation> evaluations) {
        Objects.requireNonNull(split); Objects.requireNonNull(evaluations);
        if (evaluations.size() != FineTuningModelArtifact.Variant.values().length * RagCondition.values().length) {
            throw new IllegalArgumentException("complete base/fine-tuned No/Current/Oracle matrix required");
        }
        Map<FineTuningModelArtifact.Variant, Map<RagCondition, FineTuningEvaluation>> matrix = new EnumMap<>(FineTuningModelArtifact.Variant.class);
        for (var variant : FineTuningModelArtifact.Variant.values()) matrix.put(variant, new EnumMap<>(RagCondition.class));
        for (var evaluation : evaluations) {
            if (!split.version().equals(evaluation.artifact().splitVersion())
                    || !split.trainingDigest().equals(evaluation.artifact().trainingDigest())) throw new IllegalArgumentException("artifact split mismatch");
            if (matrix.get(evaluation.artifact().variant()).put(evaluation.ragCondition(), evaluation) != null) throw new IllegalArgumentException("duplicate evaluation");
        }
        for (var variant : matrix.values()) if (variant.size() != RagCondition.values().length) throw new IllegalArgumentException("missing evaluation");
        var first = evaluations.getFirst().configuration();
        if (evaluations.stream().anyMatch(e -> !sameSettings(first, e.configuration()))) throw new IllegalArgumentException("evaluation settings differ");
        var base = matrix.get(FineTuningModelArtifact.Variant.BASE).get(RagCondition.CURRENT_RAG).metrics();
        var tuned = matrix.get(FineTuningModelArtifact.Variant.FINE_TUNED).get(RagCondition.CURRENT_RAG).metrics();
        double gain = tuned.qualityScore() - base.qualityScore();
        boolean accepted = gain >= MIN_GAIN && tuned.groundingRate() >= base.groundingRate()
                && tuned.koreanNarrationRate() >= base.koreanNarrationRate()
                && tuned.structureSuccessRate() >= base.structureSuccessRate()
                && tuned.latencyMeanMs() <= base.latencyMeanMs() && tuned.costUsd() <= base.costUsd();
        String rationale = accepted ? "fine-tuned quality gain=" + gain + " with no gated regression"
                : "quality gain=" + gain + " failed quality/grounding/Korean/structure/latency/cost gate";
        return new FineTuningDecisionReport(SCHEMA, split, evaluations, accepted ? Decision.GO : Decision.NO_GO, rationale);
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
