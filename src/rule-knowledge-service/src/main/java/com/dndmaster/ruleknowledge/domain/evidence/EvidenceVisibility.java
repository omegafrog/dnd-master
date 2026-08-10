package com.dndmaster.ruleknowledge.domain.evidence;

public enum EvidenceVisibility {
    PLAYER_VISIBLE,
    GM_ONLY,
    UNKNOWN;

    public boolean canExposeToPlayer() {
        return this == PLAYER_VISIBLE;
    }
}
