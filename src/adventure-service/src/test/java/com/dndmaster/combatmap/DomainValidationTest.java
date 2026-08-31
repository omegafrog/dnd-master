package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.domain.GridPosition;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void gridPositionRejectsNegativeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new GridPosition(-1, 0));
        assertEquals(2, new GridPosition(2, 3).x());
    }
}
