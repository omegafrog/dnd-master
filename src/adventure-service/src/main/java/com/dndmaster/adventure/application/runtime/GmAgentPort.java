package com.dndmaster.adventure.application.runtime;

public interface GmAgentPort {
    GmPlanResult plan(GmContextEnvelope context);
}
