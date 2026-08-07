package com.dndmaster.aigamemaster.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GmBenchmarkRunner {
    public GmBenchmarkReport run(GmBenchmarkCorpus corpus, GmBenchmarkConfig config, GmBenchmarkExecutor executor) {
        Objects.requireNonNull(corpus);
        Objects.requireNonNull(config);
        Objects.requireNonNull(executor);
        if (!corpus.version().equals(config.corpusVersion())) throw new IllegalArgumentException("corpus version mismatch");
        List<GmBenchmarkRun> runs = new ArrayList<>();
        for (GmBenchmarkCase benchmarkCase : corpus.cases()) {
            for (int index = 0; index < config.repetitions(); index++) {
                GmBenchmarkRun.TemperatureState state = index == 0
                        ? GmBenchmarkRun.TemperatureState.COLD : GmBenchmarkRun.TemperatureState.WARM;
                GmBenchmarkExecution execution = Objects.requireNonNull(executor.execute(benchmarkCase, config, state));
                runs.add(new GmBenchmarkRun(corpus.caseIdentity(benchmarkCase), index, state,
                        execution.rawResponse(), execution.structuredSuccess(), execution.secretLeak(),
                        execution.citationCorrect(), execution.latencyMs(), execution.timing()));
            }
        }
        List<GmBenchmarkReport.GmBenchmarkCaseMetrics> metrics = corpus.cases().stream().map(benchmarkCase -> {
            String identity = corpus.caseIdentity(benchmarkCase);
            return new GmBenchmarkReport.GmBenchmarkCaseMetrics(identity,
                    GmBenchmarkAggregator.aggregate(runs.stream().filter(run -> run.caseId().equals(identity)).toList()));
        }).toList();
        return new GmBenchmarkReport("gm-quality-baseline.v1", corpus.version(), config.model(), config.modelDigest(),
                config.temperature(), config.tokenCap(), config.contextSize(), runs, metrics,
                GmBenchmarkAggregator.aggregateAll(runs),
                GmBenchmarkAggregator.aggregateAll(runs.stream().filter(run -> run.temperatureState() == GmBenchmarkRun.TemperatureState.COLD).toList()),
                GmBenchmarkAggregator.aggregateAll(runs.stream().filter(run -> run.temperatureState() == GmBenchmarkRun.TemperatureState.WARM).toList()),
                GmLatencyMetadata.defaults(runs.size()));
    }
}
