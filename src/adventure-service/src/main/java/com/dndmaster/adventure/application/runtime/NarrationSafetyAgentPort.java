package com.dndmaster.adventure.application.runtime;

/** Typed, read-only narration safety boundary. It cannot alter a turn resolution. */
@FunctionalInterface
public interface NarrationSafetyAgentPort {
    NarrationSafetyAssessment inspect(NarrationSafetyRequest request);
}
