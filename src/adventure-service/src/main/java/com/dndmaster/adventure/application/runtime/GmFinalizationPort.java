package com.dndmaster.adventure.application.runtime;

import java.util.List;

/**
 * Read-only boundary for turning a selected plan and already-authoritative tool
 * outcomes into the runtime plan that can be committed.
 */
public interface GmFinalizationPort {
    RuntimePlan finalize(RuntimePlan selectedPlan, List<RuntimeCommandOutcome> outcomes);
}
