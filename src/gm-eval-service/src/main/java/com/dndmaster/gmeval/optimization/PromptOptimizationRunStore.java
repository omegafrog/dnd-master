package com.dndmaster.gmeval.optimization;

import java.util.List;

public interface PromptOptimizationRunStore {
    List<PromptOptimizationRun> load();

    void save(List<PromptOptimizationRun> runs);
}
