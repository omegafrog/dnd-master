package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapBoundary;
import com.dndmaster.combatmap.domain.MapLayer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Removes initial tactical fog coordinates from the player-safe projection only. */
public final class PlayerSafeFogProjection {
    private PlayerSafeFogProjection() { }
    public static Set<GridPosition> filter(Set<GridPosition> cells, List<MapLayer> layers) {
        Set<GridPosition> result = new HashSet<>(cells);
        layers.stream().filter(layer -> layer.visibility() == LayerVisibility.AI_ONLY && layer.type().equals("INITIAL_FOG"))
                .flatMap(layer -> java.util.Arrays.stream(layer.value().split(";"))).forEach(value -> {
                    String[] coordinate = value.split(",");
                    if (coordinate.length != 2) return;
                    try { result.remove(new GridPosition(Integer.parseInt(coordinate[0]), Integer.parseInt(coordinate[1]))); }
                    catch (NumberFormatException ignored) { /* legacy or malformed internal layer must not break player reads */ }
                });
        return Set.copyOf(result);
    }

    public static List<MapLayer> clipBoundaries(List<MapLayer> layers, GridSpec grid, Set<GridPosition> explored) {
        return layers.stream().map(layer -> {
            if (!"MAP_BOUNDARIES".equals(layer.type())) return layer;
            List<String> visible = new java.util.ArrayList<>();
            for (String encoded : layer.value().split(";")) {
                try {
                    MapBoundary boundary = MapBoundary.parse(encoded);
                    if (boundary.inside(grid) && adjacentCells(boundary, grid).stream().allMatch(explored::contains)) {
                        visible.add(boundary.encoded());
                    }
                } catch (IllegalArgumentException ignored) { /* malformed boundary data is hidden from the player projection */ }
            }
            return visible.isEmpty() ? null : new MapLayer(layer.type(), String.join(";", visible), layer.visibility());
        }).filter(java.util.Objects::nonNull).toList();
    }

    private static List<GridPosition> adjacentCells(MapBoundary boundary, GridSpec grid) {
        if (boundary.orientation() == MapBoundary.Orientation.HORIZONTAL) {
            List<GridPosition> result = new java.util.ArrayList<>(2);
            if (boundary.y() > 0) result.add(new GridPosition(boundary.x(), boundary.y() - 1));
            if (boundary.y() < grid.height()) result.add(new GridPosition(boundary.x(), boundary.y()));
            return result;
        }
        List<GridPosition> result = new java.util.ArrayList<>(2);
        if (boundary.x() > 0) result.add(new GridPosition(boundary.x() - 1, boundary.y()));
        if (boundary.x() < grid.width()) result.add(new GridPosition(boundary.x(), boundary.y()));
        return result;
    }
}
