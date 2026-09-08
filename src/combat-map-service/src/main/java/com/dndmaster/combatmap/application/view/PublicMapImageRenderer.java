package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Set;

@FunctionalInterface
public interface PublicMapImageRenderer {
    byte[] render(String sourceImage, double originX, double originY, double cellSize, Set<GridPosition> explored);
}
