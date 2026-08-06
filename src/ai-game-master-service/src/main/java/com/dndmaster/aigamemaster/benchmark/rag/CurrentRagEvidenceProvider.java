package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import java.util.List;
import java.util.Objects;

public final class CurrentRagEvidenceProvider implements RagEvidenceProvider {
    private final RagEvidenceProvider delegate;
    public CurrentRagEvidenceProvider(RagEvidenceProvider delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public List<String> evidence(GmBenchmarkCase benchmarkCase) { return List.copyOf(delegate.evidence(benchmarkCase)); }
}
