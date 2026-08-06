package com.dndmaster.adventure.application.runtime;

public record VersionedRuntimeSnapshots(String characterSnapshot, long characterVersion, String mapSnapshot, long mapVersion,
                                        String factSnapshot, long factVersion, String clockSnapshot, long clockVersion,
                                        String currentTurn, String currentRound, String location, String mapState,
                                        String fogOfWar, boolean pendingTool, boolean pendingMapCandidate, boolean saveFailure) {
    public VersionedRuntimeSnapshots(String characterSnapshot, long characterVersion, String mapSnapshot, long mapVersion,
                                     String factSnapshot, long factVersion, String clockSnapshot, long clockVersion) {
        this(characterSnapshot, characterVersion, mapSnapshot, mapVersion, factSnapshot, factVersion, clockSnapshot, clockVersion,
                "", "", "", mapSnapshot, mapSnapshot, false, false, false);
    }
    public VersionedRuntimeSnapshots {
        if (characterSnapshot == null || mapSnapshot == null || factSnapshot == null || clockSnapshot == null
                || currentTurn == null || currentRound == null || location == null || mapState == null || fogOfWar == null
                || characterVersion < 0 || mapVersion < 0 || factVersion < 0 || clockVersion < 0) {
            throw new IllegalArgumentException("valid versioned runtime snapshots required");
        }
    }
}
