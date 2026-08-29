package com.dndmaster.gmeval.registry;

import java.util.List;

public interface PromptRegistryStore {
    List<PromptArtifact> load();

    void save(List<PromptArtifact> artifacts);
}
