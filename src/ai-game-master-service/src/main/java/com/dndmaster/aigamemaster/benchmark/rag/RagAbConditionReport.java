package com.dndmaster.aigamemaster.benchmark.rag;

import java.util.List;
import java.util.Map;

public record RagAbConditionReport(RagAbCondition condition, List<RagAbExecution> runs, RagAbMetrics metrics,
        Map<String, RagAbMetrics> caseMetrics) {
    public RagAbConditionReport {
        runs = List.copyOf(runs);
        caseMetrics = Map.copyOf(caseMetrics);
    }
}
