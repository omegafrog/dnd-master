package com.dndmaster.aigamemaster.benchmark.finetuning;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runs both provider identities against the same split and generation settings. */
public final class FineTuningEvaluationRunner {
    public FineTuningDecisionReport run(FineTuningDatasetSplit split, FineTuningModelArtifact base,
                                        FineTuningModelArtifact fineTuned, GmBenchmarkConfig configuration,
                                        FineTuningEvaluationExecutor executor) {
        Objects.requireNonNull(split); Objects.requireNonNull(base); Objects.requireNonNull(fineTuned);
        Objects.requireNonNull(configuration); Objects.requireNonNull(executor);
        if (base.variant() != FineTuningModelArtifact.Variant.BASE || fineTuned.variant() != FineTuningModelArtifact.Variant.FINE_TUNED) {
            throw new IllegalArgumentException("base and fine-tuned artifacts required");
        }
        List<FineTuningEvaluation> evaluations = new ArrayList<>();
        for (var artifact : List.of(base, fineTuned)) for (var condition : RagCondition.values()) {
            evaluations.add(new FineTuningEvaluation(artifact, condition, configuration,
                    Objects.requireNonNull(executor.evaluate(artifact, condition, split, configuration))));
        }
        return FineTuningDecisionReport.create(split, evaluations);
    }
}
