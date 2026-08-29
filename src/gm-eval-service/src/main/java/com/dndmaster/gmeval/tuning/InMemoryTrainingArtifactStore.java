package com.dndmaster.gmeval.tuning;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryTrainingArtifactStore implements TrainingArtifactStore {
    private List<TrainingArtifact> artifacts = List.of();

    @Override public List<TrainingArtifact> load() { return List.copyOf(artifacts); }
    @Override public void save(List<TrainingArtifact> next) { artifacts = List.copyOf(new ArrayList<>(next)); }
}
