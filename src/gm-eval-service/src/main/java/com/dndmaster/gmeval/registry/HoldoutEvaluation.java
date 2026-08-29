package com.dndmaster.gmeval.registry;

import com.dndmaster.gmeval.optimization.BaselineDelta;
import com.dndmaster.gmeval.optimization.HardMetric;
import com.dndmaster.gmeval.optimization.MetricVector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable final-set evidence. Holdout data is never used to search or select a candidate. */
public record HoldoutEvaluation(String datasetVersion, String evalVersion, MetricVector metrics,
                                MetricVector baselineMetrics, BaselineDelta baselineDelta,
                                String resultFingerprint) {
    public HoldoutEvaluation {
        if (datasetVersion == null || datasetVersion.isBlank()) throw new IllegalArgumentException("holdout dataset required");
        if (evalVersion == null || evalVersion.isBlank()) throw new IllegalArgumentException("holdout eval required");
        metrics = Objects.requireNonNull(metrics, "holdout metrics required");
        baselineMetrics = Objects.requireNonNull(baselineMetrics, "holdout baseline metrics required");
        baselineDelta = Objects.requireNonNull(baselineDelta, "holdout baseline delta required");
        resultFingerprint = resultFingerprint == null || resultFingerprint.isBlank()
                ? fingerprint(datasetVersion, evalVersion, metrics, baselineMetrics) : resultFingerprint;
    }

    public static HoldoutEvaluation of(String datasetVersion, String evalVersion,
                                       MetricVector metrics, MetricVector baselineMetrics) {
        return new HoldoutEvaluation(datasetVersion, evalVersion, metrics, baselineMetrics,
                BaselineDelta.between(metrics, baselineMetrics), null);
    }

    public boolean passed() {
        return java.util.Arrays.stream(HardMetric.values()).allMatch(metric -> metrics.hard(metric) <= baselineMetrics.hard(metric));
    }

    private static String fingerprint(String dataset, String eval, MetricVector metrics, MetricVector baseline) {
        String canonical = dataset + "|" + eval + "|" + metrics + "|" + baseline;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
