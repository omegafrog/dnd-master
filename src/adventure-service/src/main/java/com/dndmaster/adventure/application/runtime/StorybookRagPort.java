package com.dndmaster.adventure.application.runtime;

/** Storybook source access is owned by the adapter, never by an AI agent. */
@FunctionalInterface
public interface StorybookRagPort {
    StorybookRagResult search(StorybookRagRequest request);
}
