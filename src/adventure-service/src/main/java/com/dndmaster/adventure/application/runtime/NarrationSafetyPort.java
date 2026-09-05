package com.dndmaster.adventure.application.runtime;

public interface NarrationSafetyPort extends NarrationSafetyAgentPort {
    NarrationSafetyAssessment assess(NarrationSafetyRequest request);

    @Override
    default NarrationSafetyAssessment inspect(NarrationSafetyRequest request) {
        return assess(request);
    }
}
