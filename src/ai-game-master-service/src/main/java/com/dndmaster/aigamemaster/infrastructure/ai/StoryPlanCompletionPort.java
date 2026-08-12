package com.dndmaster.aigamemaster.infrastructure.ai;

/** Provider-neutral completion port for source-grounded adventure plans. */
@FunctionalInterface
public interface StoryPlanCompletionPort {
    String complete(String operationId, String prompt);
}
