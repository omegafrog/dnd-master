package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.List;

@FunctionalInterface
public interface RagAbExecutor {
    RagAbExecution execute(RagAbCase benchmarkCase, RagAbCondition condition, List<String> evidence,
            GmBenchmarkConfig unchangedConfiguration);
}
