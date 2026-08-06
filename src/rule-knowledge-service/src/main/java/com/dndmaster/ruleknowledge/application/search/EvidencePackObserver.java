package com.dndmaster.ruleknowledge.application.search;

@FunctionalInterface
public interface EvidencePackObserver {
    void onAssembled(int candidateCount, int entryCount, boolean degraded, long elapsedNanos);

    static EvidencePackObserver noop() {
        return (candidateCount, entryCount, degraded, elapsedNanos) -> { };
    }
}
