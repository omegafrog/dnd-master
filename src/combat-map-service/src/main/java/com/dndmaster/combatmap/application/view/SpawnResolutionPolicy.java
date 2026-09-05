package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.*;

public final class SpawnResolutionPolicy {
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, MapActivationContext context, Optional<GridPosition> tacticalPlayerPlacement) {
        Set<GridPosition> blocked = new HashSet<>(obstacles);
        doors.stream().filter(d -> !d.open()).map(Door::position).forEach(blocked::add);
        Set<GridPosition> used = new HashSet<>(occupied);
        if (tacticalPlayerPlacement.isPresent() && valid(grid, blocked, used, tacticalPlayerPlacement.get()))
            return new SpawnResolution(tacticalPlayerPlacement.get(), SpawnResolution.Source.EXPLICIT_TACTICAL);
        if (context.spawnCandidate().isPresent() && valid(grid, blocked, used, context.spawnCandidate().get()))
            return new SpawnResolution(context.spawnCandidate().get(), SpawnResolution.Source.ACTIVATION_CANDIDATE);
        for (GridPosition candidate : boundary(grid, context.entrySide().orElse(null)))
            if (valid(grid, blocked, used, candidate)) return new SpawnResolution(candidate, SpawnResolution.Source.ENTRY_BOUNDARY);
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) {
            GridPosition candidate = new GridPosition(x, y);
            if (valid(grid, blocked, used, candidate)) return new SpawnResolution(candidate, SpawnResolution.Source.SAFE_FALLBACK);
        }
        throw new NoValidPlayerSpawnException();
    }
    private static boolean valid(GridSpec grid, Set<GridPosition> blocked, Set<GridPosition> used, GridPosition p) {
        return grid.contains(p) && !blocked.contains(p) && !used.contains(p);
    }
    private static List<GridPosition> boundary(GridSpec g, MapActivationContext.EntrySide side) {
        List<GridPosition> result = new ArrayList<>();
        if (side == null || side == MapActivationContext.EntrySide.NORTH) for (int x : centered(g.width())) result.add(new GridPosition(x,0));
        if (side == null || side == MapActivationContext.EntrySide.EAST) for (int y : centered(g.height())) result.add(new GridPosition(g.width()-1,y));
        if (side == null || side == MapActivationContext.EntrySide.SOUTH) for (int x : centered(g.width())) result.add(new GridPosition(x,g.height()-1));
        if (side == null || side == MapActivationContext.EntrySide.WEST) for (int y : centered(g.height())) result.add(new GridPosition(0,y));
        return result;
    }
    private static List<Integer> centered(int length) {
        List<Integer> result = new ArrayList<>();
        int center = length / 2;
        result.add(center);
        for (int distance = 1; result.size() < length; distance++) {
            if (center + distance < length) result.add(center + distance);
            if (center - distance >= 0) result.add(center - distance);
        }
        return result;
    }
}
