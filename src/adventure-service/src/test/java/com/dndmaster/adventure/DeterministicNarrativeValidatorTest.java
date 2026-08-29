package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.DeterministicNarrativeValidator;
import com.dndmaster.adventure.application.runtime.NarrativeVerificationContext;
import com.dndmaster.adventure.application.runtime.VerificationSeverity;
import com.dndmaster.adventure.application.runtime.VerificationViolationType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicNarrativeValidatorTest {
    @Test
    void reportsStructuredViolationsForKnownUnsafeClaims() {
        NarrativeVerificationContext context = new NarrativeVerificationContext(
                "the sealed vault opens", List.of("the bell rings"), List.of("the secret key"),
                List.of("rule mismatch"), List.of("the player decides"), List.of("the guard knows"),
                List.of("the door remains sealed"), List.of("the player decides"), List.of("the bell rings"));

        var violations = new DeterministicNarrativeValidator().validate(context,
                "The secret key opens the vault. The player decides to flee. The guard knows the key.");

        assertTrue(violations.stream().anyMatch(v -> v.type() == VerificationViolationType.SECRET_LEAK));
        assertTrue(violations.stream().anyMatch(v -> v.type() == VerificationViolationType.PLAYER_AGENCY_VIOLATION));
        assertTrue(violations.stream().anyMatch(v -> v.type() == VerificationViolationType.NPC_KNOWLEDGE_VIOLATION));
        assertTrue(violations.stream().allMatch(v -> v.severity() == VerificationSeverity.ERROR));
    }

    @Test
    void cleanDraftPasses() {
        var context = NarrativeVerificationContext.of("hall", List.of("the bell rings"));
        assertEquals(List.of(), new DeterministicNarrativeValidator().validate(context, "The bell rings."));
    }
}
