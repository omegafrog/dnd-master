package com.dndmaster.aigamemaster.infrastructure.ai;

@FunctionalInterface
public interface GmCompletionAdapter {
    <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser);
}
