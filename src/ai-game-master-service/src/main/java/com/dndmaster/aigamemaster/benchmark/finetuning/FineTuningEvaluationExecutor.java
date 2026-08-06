package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.List;

@FunctionalInterface
public interface FineTuningEvaluationExecutor {
    FineTuningMetrics evaluate(FineTuningModelArtifact artifact, RagCondition condition,
                               FineTuningDatasetSplit split, GmBenchmarkConfig configuration, List<String> evidence);
}
