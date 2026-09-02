package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.CandidateRepairPolicy;
import com.dndmaster.adventure.domain.scenario.CandidateRecoverability;
import org.junit.jupiter.api.Test;

class CandidateRepairPolicyTest {
    @Test
    void allowsAtMostOneTypedRepair() {
        CandidateRepairPolicy policy = new CandidateRepairPolicy();
        assertTrue(policy.mayRepair(CandidateRecoverability.REPAIRABLE, 0));
        assertTrue(policy.mayRepair(CandidateRecoverability.MAYBE_REPAIRABLE, 0));
        assertFalse(policy.mayRepair(CandidateRecoverability.NON_REPAIRABLE, 0));
        assertFalse(policy.mayRepair(CandidateRecoverability.REPAIRABLE, 1));
    }
}
