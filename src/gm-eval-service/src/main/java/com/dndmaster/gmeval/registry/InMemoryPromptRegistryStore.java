package com.dndmaster.gmeval.registry;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryPromptRegistryStore implements PromptRegistryStore {
    private List<PromptArtifact> artifacts = List.of();

    @Override
    public List<PromptArtifact> load() {
        return List.copyOf(artifacts);
    }

    @Override
    public void save(List<PromptArtifact> next) {
        artifacts = List.copyOf(new ArrayList<>(next));
    }
}
