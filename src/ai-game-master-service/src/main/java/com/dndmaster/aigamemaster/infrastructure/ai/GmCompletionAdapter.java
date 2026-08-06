package com.dndmaster.aigamemaster.infrastructure.ai;

@FunctionalInterface
public interface GmCompletionAdapter {
    <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser);

    default <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser,
                            GmProviderRequest provider) {
        return complete(operationId, prompt, parser);
    }
}
