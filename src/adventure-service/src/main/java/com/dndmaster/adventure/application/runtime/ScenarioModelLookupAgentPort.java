package com.dndmaster.adventure.application.runtime;

/** Typed, read-only ScenarioModel lookup boundary for the AI service. */
@FunctionalInterface
public interface ScenarioModelLookupAgentPort {
    ScenarioLookupResult lookup(ScenarioModelLookupRequest request);
}
