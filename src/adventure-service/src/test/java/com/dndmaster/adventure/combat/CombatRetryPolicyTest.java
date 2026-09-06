package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.CombatRetryPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CombatRetryPolicyTest {
    @Test
    void retries_initial_attempt_plus_two_with_one_and_two_second_backoff() {
        assertEquals(3, CombatRetryPolicy.MAX_ATTEMPTS);
        assertEquals(Duration.ZERO, CombatRetryPolicy.backoffAfterAttempt(1));
        assertEquals(Duration.ofSeconds(1), CombatRetryPolicy.backoffAfterAttempt(2));
        assertEquals(Duration.ofSeconds(2), CombatRetryPolicy.backoffAfterAttempt(3));
        assertTrue(CombatRetryPolicy.shouldRetry(new RuntimeException("transient"), 1));
        assertTrue(CombatRetryPolicy.shouldRetry(new RuntimeException("transient"), 2));
        assertFalse(CombatRetryPolicy.shouldRetry(new RuntimeException("transient"), 3));
    }
}
