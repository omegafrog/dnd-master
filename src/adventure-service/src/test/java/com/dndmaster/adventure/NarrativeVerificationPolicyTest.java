package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.NarrativeVerificationPolicy;
import com.dndmaster.adventure.application.runtime.VerificationResult;
import com.dndmaster.adventure.application.runtime.VerificationSeverity;
import com.dndmaster.adventure.application.runtime.VerificationStatus;
import com.dndmaster.adventure.application.runtime.VerificationViolation;
import com.dndmaster.adventure.application.runtime.VerificationViolationType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NarrativeVerificationPolicyTest {
    @Test
    void errorsRequireExactlyOneRewriteAndWarningsCanPass() {
        NarrativeVerificationPolicy policy = new NarrativeVerificationPolicy();
        VerificationResult warning = new VerificationResult(VerificationStatus.FAIL,
                List.of(new VerificationViolation(VerificationViolationType.TURNPLAN_DEVIATION,
                        VerificationSeverity.WARNING, "draft", "minor", "keep the planned outcome")), 0);
        VerificationResult error = new VerificationResult(VerificationStatus.FAIL,
                List.of(new VerificationViolation(VerificationViolationType.SECRET_LEAK,
                        VerificationSeverity.ERROR, "draft", "secret", "remove secret")), 0);

        assertFalse(policy.requiresRewrite(warning));
        assertTrue(policy.accepts(warning));
        assertTrue(policy.requiresRewrite(error));
        assertFalse(policy.accepts(error));
        assertFalse(policy.requiresRewrite(error.withRewriteCount(1)));
        assertFalse(policy.accepts(error.withRewriteCount(1)));
    }

    @Test
    void fingerprintsAreStableForSameResolvedTurn() {
        NarrativeVerificationPolicy policy = new NarrativeVerificationPolicy();
        assertEquals(policy.fingerprint("turn-1", "plan-1", List.of("outcome")),
                policy.fingerprint("turn-1", "plan-1", List.of("outcome")));
        assertFalse(policy.fingerprint("turn-1", "plan-1", List.of("outcome"))
                .equals(policy.fingerprint("turn-1", "plan-2", List.of("outcome"))));
    }
}
