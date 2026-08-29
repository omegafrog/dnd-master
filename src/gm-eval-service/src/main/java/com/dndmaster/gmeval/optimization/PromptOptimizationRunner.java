package com.dndmaster.gmeval.optimization;

import com.dndmaster.gmeval.registry.DatasetSplitPolicy;
import com.dndmaster.gmeval.registry.PromptArtifact;
import com.dndmaster.gmeval.registry.PromptRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline, role-scoped runner. Train searches; dev selects; holdout is never consumed. */
public final class PromptOptimizationRunner {
    private final PromptOptimizationRunRepository repository;
    private final PromptCandidateGate gate;

    public PromptOptimizationRunner(PromptOptimizationRunRepository repository) {
        this(repository, new PromptCandidateGate());
    }

    public PromptOptimizationRunner(PromptOptimizationRunRepository repository, PromptCandidateGate gate) {
        this.repository = Objects.requireNonNull(repository, "optimization repository required");
        this.gate = Objects.requireNonNull(gate, "candidate gate required");
    }

    public PromptOptimizationRun run(PromptOptimizationRequest request, PromptMetricEvaluator evaluator) {
        Objects.requireNonNull(request, "optimization request required");
        Objects.requireNonNull(evaluator, "metric evaluator required");
        DatasetSplitPolicy.validateForPhase(request.searchCases(), OptimizationPhase.SEARCH);
        DatasetSplitPolicy.validateForPhase(request.selectionCases(), OptimizationPhase.SELECTION);

        PromptCandidate baseline = baselineCandidate(request.baseline(), request.seed());
        MetricVector baselineSearch = Objects.requireNonNull(evaluator.evaluate(baseline, request.searchCases(), OptimizationPhase.SEARCH), "baseline search metrics");
        MetricVector baselineSelection = Objects.requireNonNull(evaluator.evaluate(baseline, request.selectionCases(), OptimizationPhase.SELECTION), "baseline selection metrics");
        MetricVector baselineMetrics = MetricVector.worstHard(baselineSearch, baselineSelection);

        List<PromptCandidateEvaluation> evaluations = new ArrayList<>();
        for (PromptCandidate candidate : request.candidates()) {
            // Search metrics drive candidate exploration, but are not used as the selection quality score.
            MetricVector search = Objects.requireNonNull(evaluator.evaluate(candidate, request.searchCases(), OptimizationPhase.SEARCH), "candidate search metrics");
            MetricVector selection = Objects.requireNonNull(evaluator.evaluate(candidate, request.selectionCases(), OptimizationPhase.SELECTION), "candidate selection metrics");
            MetricVector finalMetrics = MetricVector.worstHard(search, selection);
            evaluations.add(gate.evaluate(candidate, finalMetrics, baselineMetrics,
                    request.representativeOutputs().getOrDefault(candidate.candidateId(), List.of())));
        }
        PromptCandidateEvaluation selected = gate.selectBestOrEmpty(evaluations).orElse(null);
        PromptOptimizationRun run = new PromptOptimizationRun(request.runId(), request.role(), request.datasetVersion(),
                request.evalVersion(), request.seed(), request.baseline(), evaluations, selected);
        repository.save(run);
        return run;
    }

    private static PromptCandidate baselineCandidate(PromptArtifact baseline, long seed) {
        return new PromptCandidate("baseline:" + baseline.promptVersion().value(),
                new PromptArtifact(baseline.promptVersion(), baseline.parentVersion(), baseline.promptContent(),
                        baseline.outputSchema(), baseline.contextOrdering(), baseline.exemplarPlacement(), baseline.modelVersion(),
                        baseline.configurationVersion(), baseline.datasetVersion(), baseline.evalVersion(), false, baseline.status()), seed);
    }
}
