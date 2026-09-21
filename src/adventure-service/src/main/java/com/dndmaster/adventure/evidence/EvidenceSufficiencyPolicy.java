package com.dndmaster.adventure.evidence;

import java.util.Set;

/** A fixed Adventure task policy; callers cannot inject model instructions. */
public interface EvidenceSufficiencyPolicy {
    String policyId();
    Set<String> documentTypes();
    FinalInsufficiency finalInsufficiency();
    enum FinalInsufficiency { LIMITED_PLAYER_ACTION, PARTIAL_OR_INVALID_SCENARIO, OPENING_PREPARATION_FAILED, EVIDENCE_UNAVAILABLE }
}
