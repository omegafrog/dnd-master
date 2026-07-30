package com.dndmaster.aigamemaster.infrastructure.ai;

@FunctionalInterface
public interface CharacterTagCompletionPort {
    String complete(String operationId, String prompt);
}
