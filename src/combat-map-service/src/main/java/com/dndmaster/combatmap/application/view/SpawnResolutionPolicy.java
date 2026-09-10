package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.*;

public final class SpawnResolutionPolicy {
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, MapActivationContext context, Optional<GridPosition> tacticalPlayerPlacement) {
        Set<GridPosition> playable = new HashSet<>();
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) playable.add(new GridPosition(x, y));
        return resolve(grid, obstacles, doors, occupied, playable, context, tacticalPlayerPlacement);
    }
    public SpawnResolution resolve(GridSpec grid, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<GridPosition> occupied, Collection<GridPosition> playable, MapActivationContext context,
            Optional<GridPosition> tacticalPlayerPlacement) {
        Set<GridPosition> blocked = new HashSet<>(obstacles);
        doors.stream().filter(d -> !d.open()).map(Door::position).forEach(blocked::add);
        Set<GridPosition> used = new HashSet<>(occupied);
        Set<GridPosition> allowed = new HashSet<>(playable);
        if (tacticalPlayerPlacement.isPresent() && valid(grid, blocked, used, allowed, tacticalPlayerPlacement.get()))
            return new SpawnResolution(tacticalPlayerPlacement.get(), SpawnResolution.Source.EXPLICIT_TACTICAL);
        if (context.spawnCandidate().isPresent() && valid(grid, blocked, used, allowed, context.spawnCandidate().get()))
            return new SpawnResolution(context.spawnCandidate().get(), SpawnResolution.Source.ACTIVATION_CANDIDATE);
        if (context.entrySide().isPresent()) {
            for (GridPosition candidate : boundary(allowed, context.entrySide().orElseThrow()))
                if (valid(grid, blocked, used, allowed, candidate)) return new SpawnResolution(candidate, SpawnResolution.Source.ENTRY_BOUNDARY);
        }
        for (GridPosition candidate : centerOutward(grid, allowed))
            if (valid(grid, blocked, used, allowed, candidate)) return new SpawnResolution(candidate, SpawnResolution.Source.SAFE_FALLBACK);
        throw new NoValidPlayerSpawnException();
    }
    private static boolean valid(GridSpec grid, Set<GridPosition> blocked, Set<GridPosition> used, Set<GridPosition> allowed, GridPosition p) {
        return grid.contains(p) && allowed.contains(p) && !blocked.contains(p) && !used.contains(p);
    }
    private static List<GridPosition> boundary(Set<GridPosition> playable, MapActivationContext.EntrySide side) {
        if (playable.isEmpty()) return List.of();
        int edge = switch (side) {
            case NORTH -> playable.stream().mapToInt(GridPosition::y).min().orElse(0);
            case EAST -> playable.stream().mapToInt(GridPosition::x).max().orElse(0);
            case SOUTH -> playable.stream().mapToInt(GridPosition::y).max().orElse(0);
            case WEST -> playable.stream().mapToInt(GridPosition::x).min().orElse(0);
        };
        return playable.stream().filter(position -> switch (side) {
            case NORTH, SOUTH -> position.y() == edge;
            case EAST, WEST -> position.x() == edge;
        }).sorted(Comparator.comparingInt(position -> side == MapActivationContext.EntrySide.NORTH || side == MapActivationContext.EntrySide.SOUTH
                ? Math.abs(position.x() - center(playable, true)) : Math.abs(position.y() - center(playable, false)))).toList();
    }
    private static int center(Set<GridPosition> playable, boolean xAxis) {
        java.util.IntSummaryStatistics values = (xAxis ? playable.stream().mapToInt(GridPosition::x)
                : playable.stream().mapToInt(GridPosition::y)).summaryStatistics();
        return values.getCount() == 0 ? 0 : (values.getMin() + values.getMax() + 1) / 2;
    }

    private static List<GridPosition> centerOutward(GridSpec grid, Set<GridPosition> playable) {
        List<GridPosition> result = new ArrayList<>();
        int centerX = center(playable, true), centerY = center(playable, false);
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++)
            if (playable.contains(new GridPosition(x, y))) result.add(new GridPosition(x, y));
        result.sort(Comparator
                .comparingInt((GridPosition position) -> Math.max(Math.abs(position.x() - centerX), Math.abs(position.y() - centerY)))
                .thenComparingInt(GridPosition::y)
                .thenComparingInt(GridPosition::x));
        return result;
    }
}
