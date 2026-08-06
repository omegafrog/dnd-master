package com.dndmaster.aigamemaster.retrieval;

public record RetrievalQualityGate(double ruleRecallAt5, double storyRecallAt5, double retrievalP95Ms, boolean passed) {
    public static RetrievalQualityGate evaluate(RetrievalEvaluationCorpus corpus, RetrievalEvaluationReport report) {
        var rule = subset(corpus, report, "rule");
        var story = subset(corpus, report, "story");
        double ruleRecall = rule.metrics().recallAt5();
        double storyRecall = story.metrics().recallAt5();
        double p95 = report.metrics().latencyP95Ms();
        return new RetrievalQualityGate(ruleRecall, storyRecall, p95, !report.hasHardFailures() && ruleRecall >= .95 && storyRecall >= .90 && p95 <= 500);
    }
    private static RetrievalEvaluationReport subset(RetrievalEvaluationCorpus corpus, RetrievalEvaluationReport report, String type) {
        var cases = corpus.cases().stream().filter(c -> "rule".equals(type) ? "rule".equals(c.evidenceType()) : !"rule".equals(c.evidenceType())).toList();
        var ids = cases.stream().map(RetrievalEvaluationCase::id).collect(java.util.stream.Collectors.toSet());
        var results = report.results().stream().filter(r -> ids.contains(r.caseId())).toList();
        return new RetrievalEvaluationReport("retrieval-evaluation-report.v1", corpus.version(), results, RetrievalMetrics.evaluate(cases, results));
    }
    public void assertPassed() { if (!passed) throw new IllegalStateException("retrieval quality targets failed"); }
}
