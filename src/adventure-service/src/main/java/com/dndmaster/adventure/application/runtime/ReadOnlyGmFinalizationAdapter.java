package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Default finalizer. It has no capability, gateway, or saga dependency. */
public final class ReadOnlyGmFinalizationAdapter implements GmFinalizationPort {
    @Override
    public RuntimePlan finalize(RuntimePlan selectedPlan, List<RuntimeCommandOutcome> outcomes) {
        Objects.requireNonNull(selectedPlan, "selected plan must not be null");
        Objects.requireNonNull(outcomes, "tool outcomes must not be null");
        for (RuntimeCommandOutcome outcome : outcomes) {
            if (outcome == null || (outcome.status() != RuntimeCommandStatus.APPLIED
                    && outcome.status() != RuntimeCommandStatus.REJECTED)) {
                throw new IllegalStateException("finalization requires terminal tool outcomes");
            }
        }
        return selectedPlan;
    }
}
