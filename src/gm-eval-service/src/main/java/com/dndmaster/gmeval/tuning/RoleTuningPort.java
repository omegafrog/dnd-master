package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;

/** Isolated provider seam: one trainer instance owns exactly one GM role. */
public interface RoleTuningPort {
    PromptRole role();
    TrainingArtifact train(TuningTrainingRequest request);
}
