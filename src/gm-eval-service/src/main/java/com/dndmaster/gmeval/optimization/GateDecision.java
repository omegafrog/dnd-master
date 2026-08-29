package com.dndmaster.gmeval.optimization;

import java.util.List;

public record GateDecision(boolean accepted, List<HardMetric> regressions, String reason) {
    public GateDecision {
        regressions = List.copyOf(regressions == null ? List.of() : regressions);
        reason = reason == null ? "" : reason;
        if (accepted && !regressions.isEmpty()) throw new IllegalArgumentException("accepted gate cannot have regressions");
    }

    public static GateDecision pass() {
        return new GateDecision(true, List.of(), "hard metrics did not regress");
    }

    public static GateDecision rejected(List<HardMetric> regressions) {
        List<HardMetric> ordered = regressions.stream().sorted().toList();
        return new GateDecision(false, ordered, "hard metric regression: " + ordered);
    }
}
