package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.domain.GridDistance;
import com.dndmaster.combatmap.domain.GridPosition;
import org.junit.jupiter.api.Test;

class GridDistanceTest {
    @Test
    void rangeUsesTheLargestHorizontalOrRoundedUpVerticalCellDifference() {
        assertEquals(4, GridDistance.cellsBetween(new GridPosition(1, 2), 0,
                new GridPosition(5, 4), 1, 5));
        assertEquals(3, GridDistance.cellsBetween(new GridPosition(1, 2), 0,
                new GridPosition(2, 2), 11, 5));
        assertEquals(3, GridDistance.cellsBetween(new GridPosition(1, 2), 0,
                new GridPosition(2, 2), 15, 5));
    }
}
