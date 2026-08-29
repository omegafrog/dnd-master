package com.dndmaster.gmeval.registry;

import java.util.Objects;

/** Operator-facing read entrypoint; exposes registry data without mutation operations. */
public final class PromptRegistryReadService implements PromptRegistryReadPort {
    private final PromptRegistryReadPort registry;

    public PromptRegistryReadService(PromptRegistryReadPort registry) {
        this.registry = Objects.requireNonNull(registry, "prompt registry read port required");
    }

    @Override
    public PromptRuntimeConfiguration active(PromptRole role) {
        return registry.active(role);
    }

    @Override
    public java.util.List<PromptArtifact> list() {
        return registry.list();
    }
}
