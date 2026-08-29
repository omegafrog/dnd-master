package com.dndmaster.gmeval.optimization;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryPromptOptimizationRunStore implements PromptOptimizationRunStore {
    private List<PromptOptimizationRun> runs = List.of();

    @Override
    public List<PromptOptimizationRun> load() {
        return List.copyOf(runs);
    }

    @Override
    public void save(List<PromptOptimizationRun> next) {
        runs = List.copyOf(new ArrayList<>(next));
    }
}
