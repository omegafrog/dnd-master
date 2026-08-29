package com.dndmaster.gmeval.optimization;

import com.dndmaster.gmeval.registry.DatasetCaseRef;
import com.dndmaster.gmeval.registry.PromptArtifact;
import com.dndmaster.gmeval.registry.PromptRole;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PromptOptimizationRequest(String runId, PromptRole role, String datasetVersion, String evalVersion,
                                        long seed, PromptArtifact baseline, List<PromptCandidate> candidates,
                                        List<DatasetCaseRef> searchCases, List<DatasetCaseRef> selectionCases,
                                        Map<String, List<String>> representativeOutputs) {
    public PromptOptimizationRequest {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("run id required");
        role = Objects.requireNonNull(role, "prompt role required");
        if (datasetVersion == null || datasetVersion.isBlank()) throw new IllegalArgumentException("dataset version required");
        if (evalVersion == null || evalVersion.isBlank()) throw new IllegalArgumentException("eval version required");
        baseline = Objects.requireNonNull(baseline, "baseline required");
        if (!baseline.baseline()) throw new IllegalArgumentException("baseline artifact required");
        if (baseline.promptVersion().role() != role) throw new IllegalArgumentException("baseline role mismatch");
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (candidates.isEmpty()) throw new IllegalArgumentException("candidates required");
        for (PromptCandidate candidate : candidates) {
            if (candidate.role() != role) throw new IllegalArgumentException("candidate role mismatch");
            if (!candidate.promptArtifact().datasetVersion().equals(datasetVersion)) throw new IllegalArgumentException("candidate dataset mismatch");
            if (!candidate.promptArtifact().evalVersion().equals(evalVersion)) throw new IllegalArgumentException("candidate eval mismatch");
        }
        if (!baseline.datasetVersion().equals(datasetVersion) || !baseline.evalVersion().equals(evalVersion)) {
            throw new IllegalArgumentException("baseline dataset/eval mismatch");
        }
        searchCases = List.copyOf(searchCases == null ? List.of() : searchCases);
        selectionCases = List.copyOf(selectionCases == null ? List.of() : selectionCases);
        representativeOutputs = representativeOutputs == null ? Map.of() : representativeOutputs.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
