package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.List;

public record RagAbReport(String schemaVersion, String corpusVersion, GmBenchmarkConfig configuration,
        List<RagAbConditionReport> conditions, RagAbAnalysis analysis) {
    public RagAbReport { if (!"gm-quality-rag-ab.v1".equals(schemaVersion)) throw new IllegalArgumentException("unsupported RAG A/B schema"); conditions = List.copyOf(conditions); }
}
