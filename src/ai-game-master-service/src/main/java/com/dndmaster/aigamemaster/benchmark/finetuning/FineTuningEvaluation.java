package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.Objects;

public record FineTuningEvaluation(FineTuningModelArtifact artifact, RagCondition ragCondition,
                                   GmBenchmarkConfig configuration, FineTuningMetrics metrics) {
    public FineTuningEvaluation {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(ragCondition, "RAG condition");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(metrics, "metrics");
    }
}
