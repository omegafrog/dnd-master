package com.dndmaster.gmeval.registry;

import java.util.List;

/** Runtime projection. It can only be created from an approved active artifact. */
public record PromptRuntimeConfiguration(
        PromptRole role,
        PromptVersion promptVersion,
        PromptVersion parentVersion,
        String promptContent,
        String outputSchema,
        List<String> contextOrdering,
        String exemplarPlacement,
        String modelVersion,
        String configurationVersion,
        String datasetVersion,
        String evalVersion,
        String optimizationRunId) {
    public PromptRuntimeConfiguration(PromptRole role, PromptVersion promptVersion, PromptVersion parentVersion,
                                      String promptContent, String outputSchema, List<String> contextOrdering,
                                      String exemplarPlacement, String modelVersion, String configurationVersion,
                                      String datasetVersion, String evalVersion) {
        this(role, promptVersion, parentVersion, promptContent, outputSchema, contextOrdering, exemplarPlacement,
                modelVersion, configurationVersion, datasetVersion, evalVersion, null);
    }
    static PromptRuntimeConfiguration from(PromptArtifact artifact) {
        if (artifact == null || !artifact.isApproved() || artifact.status() != PromptArtifactStatus.ACTIVE) {
            throw new IllegalStateException("only an approved active prompt can be used at runtime");
        }
        return new PromptRuntimeConfiguration(artifact.promptVersion().role(), artifact.promptVersion(), artifact.parentVersion(),
                artifact.promptContent(), artifact.outputSchema(), artifact.contextOrdering(), artifact.exemplarPlacement(),
                artifact.modelVersion(), artifact.configurationVersion(), artifact.datasetVersion(), artifact.evalVersion(),
                artifact.optimizationRunId());
    }
}
