package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import java.util.List;

public final class DistractorRagEvidenceProvider implements RagEvidenceProvider {
    @Override public List<String> evidence(GmBenchmarkCase benchmarkCase) {
        throw new IllegalArgumentException("distractor provider requires RagAbCase");
    }
    public List<String> evidence(RagAbCase benchmarkCase) { return List.copyOf(benchmarkCase.distractorEvidence()); }
}
