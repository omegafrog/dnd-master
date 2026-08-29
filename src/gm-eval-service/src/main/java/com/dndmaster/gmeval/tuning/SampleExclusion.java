package com.dndmaster.gmeval.tuning;

import java.util.Objects;

public record SampleExclusion(String sampleId, TuningRejectionReason reason, String detail) {
    public SampleExclusion {
        if (sampleId == null || sampleId.isBlank()) throw new IllegalArgumentException("excluded sample id required");
        reason = Objects.requireNonNull(reason, "sample exclusion reason required");
        if (detail == null || detail.isBlank()) throw new IllegalArgumentException("sample exclusion detail required");
    }
}
