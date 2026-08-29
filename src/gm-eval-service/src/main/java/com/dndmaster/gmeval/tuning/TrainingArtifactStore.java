package com.dndmaster.gmeval.tuning;

import java.util.List;

public interface TrainingArtifactStore {
    List<TrainingArtifact> load();
    void save(List<TrainingArtifact> artifacts);
}
