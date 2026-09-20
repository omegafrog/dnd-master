package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.LineOfSightQuery;
import com.dndmaster.combatmap.domain.MapBoundary;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LineOfSightQueryTest {
    private final LineOfSightQuery query = new LineOfSightQuery();

    @Test
    void shares_the_same_geometry_rule_for_cells_and_boundaries() {
        GridPosition origin = new GridPosition(1, 1);
        GridPosition target = new GridPosition(5, 1);
        MapBoundary wall = new MapBoundary(3, 1, MapBoundary.Orientation.VERTICAL, MapBoundary.Kind.WALL, false);

        assertFalse(query.clear(origin, target, Set.of(), List.of(wall)));
        assertTrue(query.clear(origin, target, Set.of(), List.of()));
    }
}
