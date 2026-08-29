package com.dndmaster.gmeval.tuning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TrainingArtifactRepository {
    private final TrainingArtifactStore store;
    private final Map<String, TrainingArtifact> artifacts = new LinkedHashMap<>();

    public TrainingArtifactRepository(TrainingArtifactStore store) {
        this.store = Objects.requireNonNull(store, "training artifact store required");
        for (TrainingArtifact artifact : store.load()) {
            if (artifacts.put(artifact.artifactId(), artifact) != null) throw new IllegalArgumentException("duplicate training artifact");
        }
    }

    public synchronized void save(TrainingArtifact artifact) {
        Objects.requireNonNull(artifact, "training artifact required");
        TrainingArtifact previous = artifacts.get(artifact.artifactId());
        if (previous != null && !previous.equals(artifact)) throw new IllegalArgumentException("training artifact is immutable: " + artifact.artifactId());
        artifacts.put(artifact.artifactId(), artifact);
        store.save(List.copyOf(artifacts.values()));
    }

    public synchronized Optional<TrainingArtifact> find(String artifactId) { return Optional.ofNullable(artifacts.get(artifactId)); }
    public synchronized List<TrainingArtifact> list() { return List.copyOf(artifacts.values()); }
}
