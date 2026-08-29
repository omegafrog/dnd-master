package com.dndmaster.gmeval.optimization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Write aggregate plus read projection boundary for optimization runs. */
public final class PromptOptimizationRunRepository {
    private final PromptOptimizationRunStore store;
    private final Map<String, PromptOptimizationRun> runs = new LinkedHashMap<>();

    public PromptOptimizationRunRepository(PromptOptimizationRunStore store) {
        this.store = Objects.requireNonNull(store, "optimization run store required");
        for (PromptOptimizationRun run : store.load()) {
            if (runs.put(run.runId(), run) != null) throw new IllegalArgumentException("duplicate optimization run");
        }
    }

    public synchronized void save(PromptOptimizationRun run) {
        Objects.requireNonNull(run, "optimization run required");
        PromptOptimizationRun previous = runs.get(run.runId());
        if (previous != null && !previous.equals(run)) throw new IllegalArgumentException("optimization run is immutable: " + run.runId());
        runs.put(run.runId(), run);
        store.save(List.copyOf(runs.values()));
    }

    public synchronized Optional<PromptOptimizationRun> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public synchronized List<PromptOptimizationRun> list() {
        return List.copyOf(runs.values());
    }

    public synchronized PromptRunReport readProjection(String runId) {
        return find(runId).orElseThrow(() -> new IllegalArgumentException("optimization run not found: " + runId)).report();
    }
}
