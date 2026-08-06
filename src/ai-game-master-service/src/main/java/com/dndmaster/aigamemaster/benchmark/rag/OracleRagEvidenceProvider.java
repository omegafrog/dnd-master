package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import java.util.List;

public final class OracleRagEvidenceProvider implements RagEvidenceProvider {
    @Override public List<String> evidence(GmBenchmarkCase benchmarkCase) { return List.copyOf(benchmarkCase.expectedEvidence()); }
}
