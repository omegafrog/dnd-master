package com.dndmaster.aigamemaster.benchmark.finetuning;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable, digest-addressed split. Test identities can never occur in training. */
public record FineTuningDatasetSplit(String version, List<String> trainingCaseIds, List<String> testCaseIds,
                                     String trainingDigest, String testDigest) {
    public FineTuningDatasetSplit {
        version = required(version, "split version");
        trainingCaseIds = clean(trainingCaseIds, "training cases");
        testCaseIds = clean(testCaseIds, "test cases");
        trainingDigest = required(trainingDigest, "training digest");
        testDigest = required(testDigest, "test digest");
        var overlap = new HashSet<>(trainingCaseIds);
        overlap.retainAll(testCaseIds);
        if (!overlap.isEmpty()) throw new IllegalArgumentException("training and test cases overlap: " + overlap);
    }

    private static List<String> clean(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.isEmpty()) throw new IllegalArgumentException(name + " required");
        var result = values.stream().map(value -> required(value, name + " id")).distinct().toList();
        if (result.size() != values.size()) throw new IllegalArgumentException(name + " ids must be unique");
        return result;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
