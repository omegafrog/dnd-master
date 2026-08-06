package com.dndmaster.adventure.application.runtime;

public record AuthoritativeRuntimeSnapshots(String characterSnapshot, String mapSnapshot, String factSnapshot, String clockSnapshot,
                                            long characterVersion, long mapVersion, long factVersion, long clockVersion) {
    public AuthoritativeRuntimeSnapshots(String characterSnapshot, String mapSnapshot, String factSnapshot, String clockSnapshot) {
        this(characterSnapshot, mapSnapshot, factSnapshot, clockSnapshot, -1, -1, -1, -1);
    }
    public AuthoritativeRuntimeSnapshots {
        if (characterSnapshot == null || mapSnapshot == null || factSnapshot == null || clockSnapshot == null) {
            throw new IllegalArgumentException("authoritative snapshots must not be null");
        }
    }
}
