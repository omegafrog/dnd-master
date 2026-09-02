package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

public record GmToolExecutionContext(UUID adventureId, UUID ruleSetId, long adventureVersion) {
    public GmToolExecutionContext {
        if (adventureId == null || ruleSetId == null) throw new IllegalArgumentException("tool execution ids must not be null");
        if (adventureVersion < 0) throw new IllegalArgumentException("adventure version must not be negative");
    }
}
