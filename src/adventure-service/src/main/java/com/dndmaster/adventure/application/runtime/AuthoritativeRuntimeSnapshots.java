package com.dndmaster.adventure.application.runtime;

public record AuthoritativeRuntimeSnapshots(String characterSnapshot, String mapSnapshot, String factSnapshot, String clockSnapshot) {
    public AuthoritativeRuntimeSnapshots {
        if (characterSnapshot == null || mapSnapshot == null || factSnapshot == null || clockSnapshot == null) {
            throw new IllegalArgumentException("authoritative snapshots must not be null");
        }
    }
}
