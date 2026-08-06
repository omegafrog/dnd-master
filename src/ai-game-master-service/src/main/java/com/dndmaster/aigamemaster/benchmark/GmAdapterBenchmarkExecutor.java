package com.dndmaster.aigamemaster.benchmark;

import com.dndmaster.aigamemaster.api.GmAgentController;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Objects;

/** Executes baseline cases through production provider routing and derives metrics from raw JSON. */
public final class GmAdapterBenchmarkExecutor implements GmBenchmarkExecutor {
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final GmProviderRequest provider;

    public GmAdapterBenchmarkExecutor(GmCompletionAdapter adapter, ObjectMapper mapper, GmProviderRequest provider) {
        this.adapter = Objects.requireNonNull(adapter);
        this.mapper = Objects.requireNonNull(mapper);
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public GmBenchmarkExecution execute(GmBenchmarkCase benchmarkCase, GmBenchmarkConfig config,
                                        GmBenchmarkRun.TemperatureState state) {
        long started = System.nanoTime();
        String raw = adapter.complete("benchmark:" + config.corpusVersion() + ":" + benchmarkCase.id() + ":" + state,
                benchmarkCase.prompt(), response -> response, provider);
        try {
            GmAgentController.Response response = GmAgentController.requireComplete(
                    mapper.readValue(raw, GmAgentController.Response.class));
            String serialized = raw.toLowerCase(Locale.ROOT);
            boolean leak = benchmarkCase.protectedFacts().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(serialized::contains);
            String cited = mapper.writeValueAsString(response.citedEvidence()).toLowerCase(Locale.ROOT);
            boolean citation = benchmarkCase.expectedEvidence().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).allMatch(cited::contains);
            return new GmBenchmarkExecution(raw, true, leak, citation, elapsedMs(started));
        } catch (Exception failure) {
            return new GmBenchmarkExecution(raw, false, false, false, elapsedMs(started));
        }
    }

    private static double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }
}
