package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadlineBudgetTest {
    @Test
    void allocatesRetrievalWithinTotalAndPropagatesRemainingTime() {
        DeadlineBudget budget = DeadlineBudget.start(Duration.ofSeconds(10), Duration.ofSeconds(3));

        assertEquals(Duration.ofSeconds(3), budget.retrievalBudget());
        assertEquals(Duration.ofSeconds(10), budget.totalBudget());
        assertTrue(!budget.child(budget.retrievalBudget()).isNegative());
    }

    @Test
    void rejectsWorkWhenTotalDeadlineIsExhausted() {
        DeadlineBudget budget = DeadlineBudget.at(java.time.Instant.now().minusSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThrows(ProviderTimeoutException.class, () -> budget.requireRemaining(Duration.ZERO));
    }
}
