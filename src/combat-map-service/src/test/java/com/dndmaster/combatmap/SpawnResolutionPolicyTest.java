package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.application.view.MapActivationContext;
import com.dndmaster.combatmap.application.view.MapPlacementRequiredException;
import com.dndmaster.combatmap.application.view.SpawnResolution;
import com.dndmaster.combatmap.application.view.SpawnResolutionPolicy;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpawnResolutionPolicyTest {
    private final SpawnResolutionPolicy policy = new SpawnResolutionPolicy();
    private final GridSpec grid = new GridSpec(3, 3, 5, 5);

    @Test
    void acceptsOnlyExplicitOrAgentProvidedCandidatesInPriorityOrder() {
        var context = new MapActivationContext(1, Optional.of(new GridPosition(1, 1)));
        assertEquals(SpawnResolution.Source.EXPLICIT_TACTICAL,
                policy.resolve(grid, Set.of(), List.of(), Set.of(), context,
                        Optional.of(new GridPosition(2, 2))).source());
        assertEquals(SpawnResolution.Source.ACTIVATION_CANDIDATE,
                policy.resolve(grid, Set.of(), List.of(), Set.of(), context, Optional.empty()).source());

        var noContextCandidate = MapActivationContext.atStage(1);
        assertEquals(SpawnResolution.Source.AGENT_PROPOSAL,
                policy.resolve(grid, Set.of(), List.of(), Set.of(), allCells(),
                        noContextCandidate, Optional.empty(), Optional.empty(), Optional.of(new GridPosition(1, 2))).source());
    }

    @Test
    void userConfirmedPlacementIsValidatedByTheSamePolicy() {
        var result = policy.resolve(grid, Set.of(new GridPosition(1, 1)), List.of(), Set.of(),
                MapActivationContext.atStage(1), Optional.of(new GridPosition(2, 1)), Optional.empty());
        assertEquals(new GridPosition(2, 1), result.position());
        assertEquals(SpawnResolution.Source.USER_CONFIRMED, result.source());
    }

    @Test
    void invalidOrMissingCandidateRequiresPlacementInsteadOfUsingAComputedCell() {
        assertThrows(MapPlacementRequiredException.class, () -> policy.resolve(grid,
                Set.of(new GridPosition(1, 1)), List.of(), Set.of(), MapActivationContext.atStage(1), Optional.empty()));
        assertThrows(MapPlacementRequiredException.class, () -> policy.resolve(grid,
                Set.of(), List.of(new Door(new GridPosition(1, 1), false)), Set.of(),
                MapActivationContext.atStage(1), Optional.of(new GridPosition(1, 1)), Optional.empty()));
    }

    @Test
    void validatorRejectsOutsidePlayableOccupiedObstacleAndClosedDoorCells() {
        assertFalse(SpawnResolutionPolicy.isValid(grid, Set.of(), List.of(), Set.of(), Set.of(new GridPosition(0, 0)), new GridPosition(1, 1)));
        assertFalse(SpawnResolutionPolicy.isValid(grid, Set.of(new GridPosition(1, 1)), List.of(), Set.of(), allCells(), new GridPosition(1, 1)));
        assertFalse(SpawnResolutionPolicy.isValid(grid, Set.of(), List.of(), Set.of(new GridPosition(1, 1)), allCells(), new GridPosition(1, 1)));
        assertFalse(SpawnResolutionPolicy.isValid(grid, Set.of(), List.of(new Door(new GridPosition(1, 1), false)), Set.of(), allCells(), new GridPosition(1, 1)));
        assertTrue(SpawnResolutionPolicy.isValid(grid, Set.of(), List.of(new Door(new GridPosition(1, 1), true)), Set.of(), allCells(), new GridPosition(1, 1)));
    }

    private static Set<GridPosition> allCells() {
        Set<GridPosition> cells = new java.util.HashSet<>();
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) cells.add(new GridPosition(x, y));
        return cells;
    }
}
