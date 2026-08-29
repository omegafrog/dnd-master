package com.dndmaster.gmeval.optimization;

import com.dndmaster.gmeval.registry.PromptArtifact;
import com.dndmaster.gmeval.registry.PromptRole;
import java.util.List;
import java.util.Objects;

/** Immutable optimization aggregate; candidates from other roles cannot enter the run. */
public record PromptOptimizationRun(String runId, PromptRole role, String datasetVersion, String evalVersion,
                                    long seed, PromptArtifact baseline, List<PromptCandidateEvaluation> candidates,
                                    PromptCandidateEvaluation selected, PromptRunReport report) {
    public PromptOptimizationRun(String runId, PromptRole role, String datasetVersion, String evalVersion,
                                 long seed, PromptArtifact baseline, List<PromptCandidateEvaluation> candidates,
                                 PromptCandidateEvaluation selected) {
        this(runId, role, datasetVersion, evalVersion, seed, baseline, candidates, selected,
                PromptRunReport.create(runId, role, datasetVersion, evalVersion, seed,
                        Objects.requireNonNull(baseline, "baseline required").promptVersion(), candidates,
                        selected == null ? null : selected.candidate().candidateId()));
    }

    public PromptOptimizationRun {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("run id required");
        role = Objects.requireNonNull(role, "prompt role required");
        if (datasetVersion == null || datasetVersion.isBlank()) throw new IllegalArgumentException("dataset version required");
        if (evalVersion == null || evalVersion.isBlank()) throw new IllegalArgumentException("eval version required");
        baseline = Objects.requireNonNull(baseline, "baseline required");
        if (!baseline.baseline()) throw new IllegalArgumentException("baseline artifact required");
        if (baseline.promptVersion().role() != role) throw new IllegalArgumentException("baseline role mismatch");
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (candidates.isEmpty()) throw new IllegalArgumentException("candidate evaluations required");
        for (PromptCandidateEvaluation value : candidates) {
            Objects.requireNonNull(value, "candidate evaluation required");
            if (value.candidate().role() != role) throw new IllegalArgumentException("candidate role mismatch");
        }
        if (selected != null && (!candidates.contains(selected) || !selected.gate().accepted())) {
            throw new IllegalArgumentException("selected candidate must be an accepted candidate in the run");
        }
        report = report == null ? PromptRunReport.create(runId, role, datasetVersion, evalVersion, seed,
                baseline.promptVersion(), candidates, selected == null ? null : selected.candidate().candidateId()) : report;
        if (!report.runId().equals(runId) || report.role() != role) throw new IllegalArgumentException("report identity mismatch");
    }
}
