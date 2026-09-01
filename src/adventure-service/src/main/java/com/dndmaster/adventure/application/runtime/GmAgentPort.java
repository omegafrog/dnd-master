package com.dndmaster.adventure.application.runtime;

import java.util.List;

public interface GmAgentPort {
    GmPlanResult plan(GmContextEnvelope context);
    /** Supplies model-visible tool vocabulary without granting execution authority. */
    default GmPlanResult plan(GmContextEnvelope context, List<GmToolSpec> tools) { return plan(context); }
    default GmPlanResult plan(GmContextEnvelope context, TurnCapability capability) { return plan(context); }
    default GmPlanResult plan(GmContextEnvelope context, TurnCapability capability, List<GmToolSpec> tools) {
        return plan(context, capability);
    }
    default GmToolCall repair(GmContextEnvelope context, GmToolCall failedCall) { return null; }
}
