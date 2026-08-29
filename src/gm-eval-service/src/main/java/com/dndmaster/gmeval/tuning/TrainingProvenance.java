package com.dndmaster.gmeval.tuning;

/** Source identity and authorization required before text can enter training. */
public record TrainingProvenance(String sourceRef, String adventureId, String sessionId,
                                 String sceneId, boolean permissionGranted, boolean curated) {
    public TrainingProvenance {
        sourceRef = required(sourceRef, "source reference");
        adventureId = required(adventureId, "adventure id");
        sessionId = required(sessionId, "session id");
        sceneId = required(sceneId, "scene id");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
