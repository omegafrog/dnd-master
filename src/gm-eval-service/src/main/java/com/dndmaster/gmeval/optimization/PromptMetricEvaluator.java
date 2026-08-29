package com.dndmaster.gmeval.optimization;

import com.dndmaster.gmeval.registry.DatasetCaseRef;
import java.util.List;

@FunctionalInterface
public interface PromptMetricEvaluator {
    MetricVector evaluate(PromptCandidate candidate, List<DatasetCaseRef> cases, OptimizationPhase phase);
}
