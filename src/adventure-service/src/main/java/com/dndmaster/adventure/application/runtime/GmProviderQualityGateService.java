package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Production deployment decision seam. Caller must refuse deployment on exception. */
public final class GmProviderQualityGateService {
    public boolean requireDeployable(GmQualityGateReport report) {
        Objects.requireNonNull(report);
        if (!report.passed()) throw new IllegalStateException("GM provider quality gate failed");
        return true;
    }
}
