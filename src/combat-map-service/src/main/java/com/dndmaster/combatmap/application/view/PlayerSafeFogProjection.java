package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.LayerVisibility;
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
                    if (coordinate.length == 2) result.remove(new GridPosition(Integer.parseInt(coordinate[0]), Integer.parseInt(coordinate[1])));
                });
        return Set.copyOf(result);
    }
}
