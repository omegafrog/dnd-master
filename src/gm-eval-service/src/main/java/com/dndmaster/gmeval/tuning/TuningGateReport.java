package com.dndmaster.gmeval.tuning;

import java.util.List;

/** Independent gate outcomes. A soft win cannot override any failed gate. */
public record TuningGateReport(boolean hardPassed, boolean softPassed, boolean holdoutPassed,
                               boolean costPassed, boolean latencyPassed, List<String> failures) {
    public TuningGateReport {
        failures = List.copyOf(failures == null ? List.of() : failures);
    }

    public boolean passed() { return hardPassed && softPassed && holdoutPassed && costPassed && latencyPassed; }

    public static TuningGateReport evaluate(TuningMetrics base, TuningMetrics tuned,
                                            TuningMetrics holdoutBase, TuningMetrics holdoutTuned,
                                            TuningGatePolicy policy) {
        boolean hard = noHardRegression(base, tuned);
        boolean soft = tuned.softScore() >= base.softScore() + policy.minimumSoftImprovement();
        boolean holdout = noHardRegression(holdoutBase, holdoutTuned)
                && holdoutTuned.softScore() >= holdoutBase.softScore() + policy.minimumHoldoutSoftImprovement();
        boolean cost = tuned.costMicros() <= policy.maximumCostMicros();
        boolean latency = tuned.latencyMillis() <= policy.maximumLatencyMillis();
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        if (!hard) failures.add("hard-regression");
        if (!soft) failures.add("soft-improvement");
        if (!holdout) failures.add("holdout-regression");
        if (!cost) failures.add("cost-limit");
        if (!latency) failures.add("latency-limit");
        return new TuningGateReport(hard, soft, holdout, cost, latency, failures);
    }

    private static boolean noHardRegression(TuningMetrics base, TuningMetrics tuned) {
        java.util.Set<String> metrics = new java.util.HashSet<>(base.hardViolations().keySet());
        metrics.addAll(tuned.hardViolations().keySet());
        return metrics.stream().allMatch(metric -> tuned.hardViolations().getOrDefault(metric, 0)
                <= base.hardViolations().getOrDefault(metric, 0));
    }
}
