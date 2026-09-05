package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SpawnResolutionPolicyTest {
    private final SpawnResolutionPolicy policy = new SpawnResolutionPolicy();
    private final GridSpec grid = new GridSpec(3, 3, 5, 5);

    @Test void explicitTacticalThenActivationCandidateThenEntryThenSafeFallback() {
        var context = new MapActivationContext(1, Optional.of(new GridPosition(1, 1)), Optional.of(MapActivationContext.EntrySide.SOUTH));
        assertEquals(SpawnResolution.Source.EXPLICIT_TACTICAL, policy.resolve(grid, Set.of(), List.of(), Set.of(), context, Optional.of(new GridPosition(2, 2))).source());
        assertEquals(new GridPosition(1, 1), policy.resolve(grid, Set.of(), List.of(), Set.of(), context, Optional.empty()).position());
        var blocked = Set.of(new GridPosition(1, 1));
        assertEquals(new GridPosition(1, 2), policy.resolve(grid, blocked, List.of(), Set.of(), context, Optional.empty()).position());
    }

    @Test void rejectsInvalidOccupiedBlockedAndOutsideCandidates() {
        var context = new MapActivationContext(1, Optional.of(new GridPosition(3, 3)), Optional.empty());
        var result = policy.resolve(grid, Set.of(new GridPosition(1, 1)), List.of(), Set.of(new GridPosition(0, 0)), context, Optional.of(new GridPosition(1, 1)));
        assertEquals(new GridPosition(1, 0), result.position());
    }

    @Test void reportsNoValidSpawn() {
        Set<GridPosition> all = new HashSet<>();
        for (int y=0;y<3;y++) for (int x=0;x<3;x++) all.add(new GridPosition(x,y));
        assertThrows(NoValidPlayerSpawnException.class, () -> policy.resolve(grid, all, List.of(), Set.of(), MapActivationContext.atStage(1), Optional.empty()));
    }
}
