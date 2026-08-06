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
        Objects.requireNonNull(split); Objects.requireNonNull(benchmarkCase); Objects.requireNonNull(currentRag);
        Objects.requireNonNull(base); Objects.requireNonNull(fineTuned);
        Objects.requireNonNull(configuration); Objects.requireNonNull(executor);
        if (base.variant() != FineTuningModelArtifact.Variant.BASE || fineTuned.variant() != FineTuningModelArtifact.Variant.FINE_TUNED) {
            throw new IllegalArgumentException("base and fine-tuned artifacts required");
        }
        List<FineTuningEvaluation> evaluations = new ArrayList<>();
        for (var artifact : List.of(base, fineTuned)) for (var condition : RagCondition.values()) {
            List<String> evidence = switch (condition) {
                case NO_RAG -> new NoRagEvidenceProvider().evidence(benchmarkCase.source());
                case CURRENT_RAG -> new CurrentRagEvidenceProvider(currentRag).evidence(benchmarkCase.source());
                case ORACLE -> new OracleRagEvidenceProvider().evidence(benchmarkCase.source());
                case DISTRACTOR -> new DistractorRagEvidenceProvider().evidence(benchmarkCase);
            };
            evaluations.add(new FineTuningEvaluation(artifact, condition, configuration,
                    Objects.requireNonNull(executor.evaluate(artifact, condition, split, configuration, evidence))));
        }
        return FineTuningDecisionReport.create(split, evaluations);
    }
}
