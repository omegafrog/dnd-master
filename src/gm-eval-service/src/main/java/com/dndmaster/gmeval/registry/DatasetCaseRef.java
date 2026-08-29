package com.dndmaster.gmeval.registry;

import java.util.Objects;

public record DatasetCaseRef(String caseId, String datasetVersion, DatasetSplit split,
                             String adventureId, String sceneId) {
    public DatasetCaseRef {
        caseId = required(caseId, "case id");
        datasetVersion = required(datasetVersion, "dataset version");
        split = Objects.requireNonNull(split, "dataset split required");
        adventureId = required(adventureId, "adventure id");
        sceneId = required(sceneId, "scene id");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value;
    }
}
