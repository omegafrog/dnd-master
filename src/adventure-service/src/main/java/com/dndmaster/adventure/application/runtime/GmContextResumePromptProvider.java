package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

@FunctionalInterface
public interface GmContextResumePromptProvider {
    String prompt(UUID sessionId);
}
