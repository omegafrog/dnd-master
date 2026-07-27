package com.dndmaster.adventure.application.runtime;

public interface NarrationSafetyPort {
    NarrationSafetyAssessment assess(NarrationSafetyRequest request);
}
