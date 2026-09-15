package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.UUID;

@FunctionalInterface
public interface CharacterTagCompletionPort {
    String complete(String operationId, String prompt);

    default String complete(UUID soloPlayerId, String operationId, String prompt) {
        return complete(operationId, prompt);
    }
}
