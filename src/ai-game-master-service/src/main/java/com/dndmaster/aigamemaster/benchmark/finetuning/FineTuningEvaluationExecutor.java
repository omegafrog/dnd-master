package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;

@FunctionalInterface
public interface FineTuningEvaluationExecutor {
    FineTuningMetrics evaluate(FineTuningModelArtifact artifact, RagCondition condition,
                               FineTuningDatasetSplit split, GmBenchmarkConfig configuration);
}
