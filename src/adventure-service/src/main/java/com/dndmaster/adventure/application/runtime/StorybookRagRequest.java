package com.dndmaster.adventure.application.runtime;

/** Typed read-only boundary to the existing Storybook RAG adapter. */
public record StorybookRagRequest(String query) {
    public StorybookRagRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        query = query.trim();
    }
}
