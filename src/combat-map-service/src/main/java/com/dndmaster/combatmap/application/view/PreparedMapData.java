package com.dndmaster.combatmap.application.view;
import com.dndmaster.combatmap.domain.*; import java.util.*;
public record PreparedMapData(GridSpec grid,List<CombatToken> tokens,Set<GridPosition> obstacles,List<MapLayer> layers){public PreparedMapData{Objects.requireNonNull(grid);tokens=List.copyOf(tokens);obstacles=Set.copyOf(obstacles);layers=List.copyOf(layers);}}
