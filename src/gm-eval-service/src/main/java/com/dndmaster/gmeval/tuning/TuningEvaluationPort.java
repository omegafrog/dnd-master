package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.DatasetSplit;
import com.dndmaster.gmeval.registry.PromptRole;

/** Provider-neutral evaluation seam. Caller supplies identical context to each target. */
@FunctionalInterface
public interface TuningEvaluationPort {
    TuningMetrics evaluate(PromptRole role, String modelVersion, String promptVersion,
                           TuningEvaluationContext context, DatasetSplit split);
}
