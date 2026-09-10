package com.dndmaster.combatmap.application.view;
import com.dndmaster.combatmap.domain.*; import java.util.*;
public record PreparedMapData(GridSpec grid,List<CombatToken> tokens,Set<GridPosition> obstacles,List<MapLayer> layers,List<Door> doors,
                              List<MapBoundaryCandidate> candidates){
    public PreparedMapData(GridSpec grid,List<CombatToken> tokens,Set<GridPosition> obstacles,List<MapLayer> layers){
        this(grid,tokens,obstacles,layers,List.of(),List.of());
    }
    public PreparedMapData(GridSpec grid,List<CombatToken> tokens,Set<GridPosition> obstacles,List<MapLayer> layers,List<Door> doors){
        this(grid,tokens,obstacles,layers,doors,List.of());
    }
    public PreparedMapData{
        Objects.requireNonNull(grid); tokens=List.copyOf(tokens); obstacles=Set.copyOf(obstacles);
        layers=List.copyOf(layers); doors=List.copyOf(doors); candidates=List.copyOf(candidates);
    }
}
