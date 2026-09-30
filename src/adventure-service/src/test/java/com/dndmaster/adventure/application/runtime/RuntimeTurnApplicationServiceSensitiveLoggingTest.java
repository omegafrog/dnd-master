package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RuntimeTurnApplicationServiceSensitiveLoggingTest {
    @Test
    void combat_grounding_log_excludes_raw_identifiers_and_proposal_content(CapturedOutput output) {
        RuntimeTurnApplicationService.logCombatGroundingRejected(
                new IllegalArgumentException("COMBAT_STAT_BLOCK_NOT_FOUND private-adventure-id=adv-secret "
                        + "enemyKey=hidden-rat displayName=Hidden Rat"),
                2, 3, 4);

        String log = output.getAll();
        assertTrue(log.contains("category=IllegalArgumentException"));
        assertTrue(log.contains("proposalCount=2"));
        assertTrue(log.contains("storybookEvidenceCount=3"));
        assertTrue(log.contains("rulebookEvidenceCount=4"));
        assertFalse(log.contains("adv-secret"));
        assertFalse(log.contains("hidden-rat"));
        assertFalse(log.contains("Hidden Rat"));
    }
}
