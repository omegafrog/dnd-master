package com.dndmaster.aigamemaster.retrieval;

import java.util.ArrayList;

public final class RetrievalEvaluationRunner {
    public RetrievalMetrics run(RetrievalEvaluationCorpus corpus, RetrievalEvaluationPort port) {
        var report = runReport(corpus, port);
        report.assertNoHardFailures();
        return report.metrics();
    }

    public void validateScopes(RetrievalEvaluationCorpus corpus, RetrievalEvaluationPort port) {
        for (var evaluationCase : corpus.cases()) port.validateScope(evaluationCase);
    }

    public RetrievalEvaluationReport runReport(RetrievalEvaluationCorpus corpus, RetrievalEvaluationPort port) {
        var results = new ArrayList<RetrievalEvaluationResult>();
        for (var evaluationCase : corpus.cases()) {
            var result = port.retrieve(evaluationCase, 5);
            if (result == null) throw new IllegalStateException("retrieval adapter returned null");
            results.add(result);
        }
        return new RetrievalEvaluationReport("retrieval-evaluation-report.v1", corpus.version(), results,
                RetrievalMetrics.evaluate(corpus.cases(), results));
    }
}
