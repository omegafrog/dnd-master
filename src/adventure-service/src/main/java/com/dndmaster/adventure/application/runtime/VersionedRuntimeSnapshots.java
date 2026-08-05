package com.dndmaster.adventure.application.runtime;

public record VersionedRuntimeSnapshots(String characterSnapshot, long characterVersion, String mapSnapshot, long mapVersion,
                                        String factSnapshot, long factVersion, String clockSnapshot, long clockVersion) {
    public VersionedRuntimeSnapshots {
        if (characterSnapshot == null || mapSnapshot == null || factSnapshot == null || clockSnapshot == null
                || characterVersion < 0 || mapVersion < 0 || factVersion < 0 || clockVersion < 0) {
            throw new IllegalArgumentException("valid versioned runtime snapshots required");
        }
    }
}
