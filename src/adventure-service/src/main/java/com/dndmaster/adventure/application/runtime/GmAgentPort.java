package com.dndmaster.adventure.application.runtime;

public interface GmAgentPort {
    GmPlanResult plan(GmContextEnvelope context);
    default GmToolCall repair(GmContextEnvelope context, GmToolCall failedCall) { return null; }
}
