package com.dndmaster.aigamemaster.benchmark.finetuning;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable, digest-addressed train/validation/frozen-holdout split. */
public record FineTuningDatasetSplit(String version, List<String> trainingCaseIds, List<String> validationCaseIds,
                                     List<String> holdoutCaseIds, String trainingDigest, String validationDigest,
                                     String holdoutDigest) {
    /** Compatibility constructor for the original train/test split contract. */
    public FineTuningDatasetSplit(String version, List<String> trainingCaseIds, List<String> testCaseIds,
                                  String trainingDigest, String testDigest) {
        this(version, trainingCaseIds, List.of("legacy-validation"), testCaseIds,
                trainingDigest, "sha256:legacy-validation", testDigest);
    }

    public FineTuningDatasetSplit {
        version = required(version, "split version");
        trainingCaseIds = clean(trainingCaseIds, "training cases");
        validationCaseIds = clean(validationCaseIds, "validation cases");
        holdoutCaseIds = clean(holdoutCaseIds, "holdout cases");
        trainingDigest = required(trainingDigest, "training digest");
        validationDigest = required(validationDigest, "validation digest");
        holdoutDigest = required(holdoutDigest, "holdout digest");
        rejectOverlap(trainingCaseIds, validationCaseIds, "training and validation cases");
        rejectOverlap(trainingCaseIds, holdoutCaseIds, "training and holdout cases");
        rejectOverlap(validationCaseIds, holdoutCaseIds, "validation and holdout cases");
        var digests = List.of(trainingDigest, validationDigest, holdoutDigest);
        if (new HashSet<>(digests).size() != digests.size()) {
            throw new IllegalArgumentException("training, validation, and holdout content digests must be disjoint");
        }
    }

    /** Legacy name retained for callers that still refer to the holdout as test data. */
    public List<String> testCaseIds() { return holdoutCaseIds; }
    public String testDigest() { return holdoutDigest; }

    private static void rejectOverlap(List<String> left, List<String> right, String name) {
        var overlap = new HashSet<>(left);
        overlap.retainAll(right);
        if (!overlap.isEmpty()) throw new IllegalArgumentException(name + " overlap: " + overlap);
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
