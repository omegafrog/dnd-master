package com.dndmaster.gmeval.registry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Prevents case, adventure, and scene leakage between optimization splits. */
public final class DatasetSplitPolicy {
    private DatasetSplitPolicy() {}

    public static void validate(List<DatasetCaseRef> cases) {
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("dataset split must not be empty");
        Set<String> caseIds = new HashSet<>();
        Map<String, DatasetSplit> adventures = new HashMap<>();
        Map<String, DatasetSplit> scenes = new HashMap<>();
        String datasetVersion = null;
        for (DatasetCaseRef value : cases) {
            Objects.requireNonNull(value, "dataset case required");
            if (!caseIds.add(value.caseId())) throw new IllegalArgumentException("duplicate dataset case: " + value.caseId());
            if (datasetVersion == null) datasetVersion = value.datasetVersion();
            if (!datasetVersion.equals(value.datasetVersion())) throw new IllegalArgumentException("mixed dataset versions");
            checkNoCrossSplitLeak(adventures, value.adventureId(), value.split(), "adventure");
            checkNoCrossSplitLeak(scenes, value.sceneId(), value.split(), "scene");
        }
    }

    public static void validateForUsage(List<DatasetCaseRef> cases, DatasetUsage usage) {
        Objects.requireNonNull(usage, "dataset usage required");
        validate(cases);
        if (usage != DatasetUsage.HOLDOUT_EVALUATION && cases.stream().anyMatch(value -> value.split() == DatasetSplit.HOLDOUT)) {
            throw new IllegalArgumentException("holdout cases cannot be used for " + usage.name().toLowerCase(Locale.ROOT));
        }
    }

    private static void checkNoCrossSplitLeak(Map<String, DatasetSplit> seen, String key,
                                               DatasetSplit split, String kind) {
        DatasetSplit previous = seen.putIfAbsent(key, split);
        if (previous != null && previous != split) {
            throw new IllegalArgumentException(kind + " appears in multiple dataset splits: " + key);
        }
    }
}
