package com.dndmaster.aigamemaster.infrastructure.ai;

import java.nio.file.Path;
import java.time.Duration;

/** Codex OAuth adapter. Authentication is supplied by the local Codex CLI session. */
public final class CodexCliStoryPlanAdapter implements StoryPlanCompletionPort {
    private final CodexAppServerClient client;
    private final String model;
    private final String reasoning;

    public CodexCliStoryPlanAdapter(String executable, String model, Path workDirectory, Duration timeout) {
        this(executable, model, workDirectory, timeout, "medium");
    }

    public CodexCliStoryPlanAdapter(String executable, String model, Path workDirectory, Duration timeout, String reasoning) {
        this.client = CodexAppServerClient.shared(executable, workDirectory, timeout, new com.fasterxml.jackson.databind.ObjectMapper());
        this.model = model;
        this.reasoning = reasoning;
    }

    @Override
    public String complete(String operationId, String prompt) {
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        return client.complete(operationId, prompt, model, reasoning);
    }
}
