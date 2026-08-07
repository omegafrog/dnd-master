package com.dndmaster.aigamemaster.benchmark.rag;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

/** Deterministic paired permutation test plus bootstrap confidence interval. */
public final class RagAbPairedStatistics {
    private static final int RESAMPLES = 10_000;

    private RagAbPairedStatistics() {}

    public static Result analyze(List<Double> current, List<Double> baseline, double alpha, long seed) {
        Objects.requireNonNull(current); Objects.requireNonNull(baseline);
        if (current.isEmpty() || current.size() != baseline.size()) throw new IllegalArgumentException("paired samples required");
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be between 0 and 1");
        double[] differences = IntStream.range(0, current.size()).mapToDouble(i -> {
            double value = current.get(i) - baseline.get(i);
            if (!Double.isFinite(value)) throw new IllegalArgumentException("samples must be finite");
            return value;
        }).toArray();
        double effect = mean(differences);
        Random random = new Random(seed);
        double[] bootstrap = new double[RESAMPLES];
        for (int sample = 0; sample < RESAMPLES; sample++) {
            double total = 0;
            for (int i = 0; i < differences.length; i++) total += differences[random.nextInt(differences.length)];
            bootstrap[sample] = total / differences.length;
        }
        java.util.Arrays.sort(bootstrap);
        double low = percentile(bootstrap, alpha / 2), high = percentile(bootstrap, 1 - alpha / 2);
        long extreme = 0;
        int exactLimit = 16;
        long totalPermutations = differences.length <= exactLimit ? 1L << differences.length : RESAMPLES;
        if (differences.length <= exactLimit) {
            for (long mask = 0; mask < totalPermutations; mask++) {
                double sum = 0;
                for (int i = 0; i < differences.length; i++) sum += ((mask & (1L << i)) == 0 ? -1 : 1) * differences[i];
                if (Math.abs(sum / differences.length) >= Math.abs(effect)) extreme++;
            }
        } else {
            for (int sample = 0; sample < totalPermutations; sample++) {
                double sum = 0;
                for (double difference : differences) sum += random.nextBoolean() ? difference : -difference;
                if (Math.abs(sum / differences.length) >= Math.abs(effect)) extreme++;
            }
        }
        double p = (extreme + 1.0) / (totalPermutations + 1.0);
        return new Result(effect, low, high, p, p <= alpha && low > 0);
    }

    private static double mean(double[] values) { return java.util.Arrays.stream(values).average().orElseThrow(); }
    private static double percentile(double[] values, double p) {
        double x = p * (values.length - 1); int low = (int) Math.floor(x), high = (int) Math.ceil(x);
        return values[low] + (values[high] - values[low]) * (x - low);
    }

    public record Result(double effect, double confidenceLow, double confidenceHigh, double pValue, boolean significant) {}
}
