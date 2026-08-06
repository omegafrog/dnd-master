package com.dndmaster.adventure.application.runtime;

public interface GmAgentPort {
    GmPlanResult plan(GmContextEnvelope context);
    default GmPlanResult plan(GmContextEnvelope context, TurnCapability capability) { return plan(context); }
    default GmToolCall repair(GmContextEnvelope context, GmToolCall failedCall) { return null; }
}
