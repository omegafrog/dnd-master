package com.dndmaster.gmeval.registry;

import java.util.List;

public interface PromptRegistryReadPort {
    PromptRuntimeConfiguration active(PromptRole role);

    List<PromptArtifact> list();
}
