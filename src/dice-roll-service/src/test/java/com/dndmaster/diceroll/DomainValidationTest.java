package com.dndmaster.diceroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.diceroll.domain.DiceExpression;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void diceExpressionEnforcesSafeBounds() {
        assertThrows(IllegalArgumentException.class, () -> new DiceExpression(0, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiceExpression(1, 1, 0));
        assertEquals(20, new DiceExpression(1, 20, 3).sides());
    }
}
