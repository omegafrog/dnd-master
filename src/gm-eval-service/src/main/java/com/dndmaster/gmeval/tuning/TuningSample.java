package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.DatasetSplit;
import com.dndmaster.gmeval.registry.PromptRole;
import java.util.List;
import java.util.Objects;

/** A candidate training example with auditable provenance and failure labels. */
public record TuningSample(String sampleId, PromptRole role, String datasetVersion, DatasetSplit split,
                           TrainingProvenance provenance, List<FailureEvidence> evidence) {
    public TuningSample {
        sampleId = required(sampleId, "sample id");
        role = Objects.requireNonNull(role, "sample role required");
        datasetVersion = required(datasetVersion, "sample dataset version");
        split = Objects.requireNonNull(split, "sample split required");
        provenance = Objects.requireNonNull(provenance, "training provenance required");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }

    public TuningSample(String sampleId, PromptRole role, String datasetVersion, DatasetSplit split,
                        String adventureId, String sessionId, String sceneId, String sourceRef,
                        boolean permissionGranted, boolean curated, List<FailureEvidence> evidence) {
        this(sampleId, role, datasetVersion, split,
                new TrainingProvenance(sourceRef, adventureId, sessionId, sceneId, permissionGranted, curated), evidence);
    }

    public boolean unsafe() {
        return !provenance.permissionGranted() || !provenance.curated()
                || evidence.stream().anyMatch(value -> !value.resolved()
                || value.category() == TuningFailureCategory.PERMISSION_UNCLEAR);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
