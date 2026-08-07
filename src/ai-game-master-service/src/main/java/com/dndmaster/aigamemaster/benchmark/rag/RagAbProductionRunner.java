package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.api.GmAgentController;
import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Application-facing entrypoint: production Current RAG + configured provider adapter. */
public final class RagAbProductionRunner {
    private final RagAbRunner runner;
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final GmProviderRequest provider;

    public RagAbProductionRunner(RagEvidenceProvider currentRag, GmCompletionAdapter adapter,
                                 ObjectMapper mapper, GmProviderRequest provider) {
        this.runner = new RagAbRunner(currentRag);
        this.adapter = Objects.requireNonNull(adapter);
        this.mapper = Objects.requireNonNull(mapper);
        this.provider = Objects.requireNonNull(provider);
    }

    public RagAbReport run(RagAbCorpus corpus, GmBenchmarkConfig config, List<RagAbReviewerRecord> reviewers) {
        return runner.run(corpus, config, (benchmarkCase, condition, evidence, unchanged) -> execute(
                benchmarkCase, condition, evidence, unchanged), reviewers);
    }

    private RagAbExecution execute(RagAbCase benchmarkCase, RagAbCondition condition, List<String> evidence,
                                   GmBenchmarkConfig config) {
        long started = System.nanoTime();
        String raw = adapter.complete("rag-ab:" + config.corpusVersion() + ":" + benchmarkCase.id() + ":" + condition,
                prompt(benchmarkCase, condition, evidence), response -> response, provider);
        try {
            var response = GmAgentController.requireComplete(mapper.readValue(raw, GmAgentController.Response.class));
            String serialized = mapper.writeValueAsString(response).toLowerCase(Locale.ROOT);
            boolean cited = benchmarkCase.source().expectedEvidence().stream().allMatch(expected -> serialized.contains(expected.toLowerCase(Locale.ROOT)));
            boolean leak = benchmarkCase.source().protectedFacts().stream().anyMatch(secret -> raw.toLowerCase(Locale.ROOT).contains(secret.toLowerCase(Locale.ROOT)));
            boolean stateChange = response.stateDelta() != null && !response.stateDelta().isEmpty();
            return new RagAbExecution(true, cited, cited, stateChange, leak, stateChange, true, 1, elapsed(started), raw);
        } catch (Exception failure) {
            return new RagAbExecution(false, false, false, false, false, false, false, 1, elapsed(started), raw);
        }
    }

    private static String prompt(RagAbCase benchmarkCase, RagAbCondition condition, List<String> evidence) {
        return "RAG condition: " + condition + "\nEvidence: " + evidence + "\nScenario: " + benchmarkCase.source().prompt();
    }
    private static double elapsed(long started) { return (System.nanoTime() - started) / 1_000_000.0; }
}
