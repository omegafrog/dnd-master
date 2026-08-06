package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RagAbRunner {
    private final RagEvidenceProvider currentRag;

    public RagAbRunner() { this(c -> { throw new IllegalStateException("Current RAG provider required"); }); }
    public RagAbRunner(RagEvidenceProvider currentRag) { this.currentRag = Objects.requireNonNull(currentRag); }

    public RagAbReport run(RagAbCorpus corpus, GmBenchmarkConfig config, RagAbExecutor executor) {
        Objects.requireNonNull(corpus); Objects.requireNonNull(config); Objects.requireNonNull(executor);
        Map<RagAbCondition, List<RagAbExecution>> grouped = new EnumMap<>(RagAbCondition.class);
        Map<RagAbCondition, Map<String, List<RagAbExecution>>> perCase = new EnumMap<>(RagAbCondition.class);
        for (RagAbCondition c : RagAbCondition.values()) grouped.put(c, new ArrayList<>());
        for (RagAbCondition c : RagAbCondition.values()) perCase.put(c, new java.util.HashMap<>());
        for (RagAbCase benchmarkCase : corpus.cases()) {
            if (!config.corpusVersion().equals(corpus.version())) throw new IllegalArgumentException("case/config corpus mismatch");
            for (RagAbCondition condition : RagAbCondition.values()) {
                List<String> evidence = switch (condition) {
                    case NO_RAG -> new NoRagEvidenceProvider().evidence(benchmarkCase.source());
                    case CURRENT_RAG -> currentRag.evidence(benchmarkCase.source());
                    case ORACLE -> new OracleRagEvidenceProvider().evidence(benchmarkCase.source());
                    case DISTRACTOR -> new DistractorRagEvidenceProvider().evidence(benchmarkCase);
                };
                for (int i = 0; i < config.repetitions(); i++) {
                    var execution = Objects.requireNonNull(executor.execute(benchmarkCase, condition, evidence, config))
                            .withRetrievalRecall(recall(evidence, benchmarkCase.source().expectedEvidence()));
                    grouped.get(condition).add(execution);
                    perCase.get(condition).computeIfAbsent(benchmarkCase.id(), ignored -> new ArrayList<>()).add(execution);
                }
            }
        }
        List<RagAbConditionReport> reports = grouped.entrySet().stream().map(e -> new RagAbConditionReport(e.getKey(), e.getValue(), RagAbMetrics.aggregate(e.getValue()),
                perCase.get(e.getKey()).entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> RagAbMetrics.aggregate(entry.getValue()))))).toList();
        return new RagAbReport("gm-quality-rag-ab.v1", corpus.version(), config, reports, analyze(reports));
    }
    private static RagAbAnalysis analyze(List<RagAbConditionReport> reports) {
        var no = find(reports, RagAbCondition.NO_RAG).metrics(); var current = find(reports, RagAbCondition.CURRENT_RAG).metrics(); var oracle = find(reports, RagAbCondition.ORACLE).metrics(); var distractor = find(reports, RagAbCondition.DISTRACTOR).metrics();
        double currentDelta = current.ruleAccuracy() - no.ruleAccuracy(), oracleDelta = oracle.ruleAccuracy() - current.ruleAccuracy();
        RagAbBottleneck bottleneck; String rationale;
        boolean accepted = currentDelta > 0
                && current.citationAccuracy() >= no.citationAccuracy()
                && current.hallucinationRate() <= no.hallucinationRate()
                && current.secretLeakRate() <= no.secretLeakRate()
                && current.prematureStateChangeRate() <= no.prematureStateChangeRate()
                && current.continuityAccuracy() >= no.continuityAccuracy()
                && current.structureSuccessRate() >= no.structureSuccessRate()
                && current.humanScoreMean() >= no.humanScoreMean()
                && current.latencyP95Ms() <= no.latencyP95Ms();
        if (current.retrievalRecallMean() >= .95 && current.structureSuccessRate() < .8) { bottleneck = RagAbBottleneck.PROMPT_CONTEXT; rationale = "retrieval recall is high but structured quality remains low"; }
        else if (oracle.ruleAccuracy() > current.ruleAccuracy() && oracle.ruleAccuracy() > no.ruleAccuracy()) { bottleneck = RagAbBottleneck.RETRIEVAL; rationale = "Oracle improves over Current and No RAG"; }
        else if (oracle.structureSuccessRate() < .8) { bottleneck = RagAbBottleneck.GENERATION; rationale = "Oracle evidence still produces low structured quality"; }
        else if (distractor.ruleAccuracy() < current.ruleAccuracy()) { bottleneck = RagAbBottleneck.VALIDATION; rationale = "distractor evidence causes quality regression"; }
        else { bottleneck = RagAbBottleneck.INCONCLUSIVE; rationale = "conditions do not isolate a dominant bottleneck"; }
        return new RagAbAnalysis(bottleneck, accepted, currentDelta, oracleDelta, rationale);
    }
    private static RagAbConditionReport find(List<RagAbConditionReport> reports, RagAbCondition c) { return reports.stream().filter(r -> r.condition() == c).findFirst().orElseThrow(); }
    private static double recall(List<String> supplied, List<String> expected) {
        if (expected.isEmpty()) return supplied.isEmpty() ? 1 : 0;
        return supplied.stream().distinct().filter(expected::contains).count() / (double) expected.size();
    }
}
