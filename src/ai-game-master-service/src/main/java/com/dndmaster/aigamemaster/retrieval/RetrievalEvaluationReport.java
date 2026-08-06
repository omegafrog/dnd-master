package com.dndmaster.aigamemaster.retrieval;

import java.util.List;

public record RetrievalEvaluationReport(
        String schemaVersion,
        String corpusVersion,
        List<RetrievalEvaluationResult> results,
        RetrievalMetrics metrics) {
    public RetrievalEvaluationReport {
        if (!"retrieval-evaluation-report.v1".equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported retrieval report schema");
        }
        results = List.copyOf(results);
    }

    public boolean hasHardFailures() {
        return metrics.secretRetrievalRate() > 0 || metrics.scopeViolationRate() > 0;
    }

    public void assertNoHardFailures() {
        if (hasHardFailures()) throw new IllegalStateException("retrieval hard failure: secret or scope violation");
    }
}
