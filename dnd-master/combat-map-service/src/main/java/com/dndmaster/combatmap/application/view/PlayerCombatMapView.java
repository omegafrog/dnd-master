package com.dndmaster.combatmap.application.view;
import com.dndmaster.combatmap.domain.*; import java.util.*;
public record PlayerCombatMapView(MapId mapId,GridSpec grid,List<CombatToken> tokens,Set<GridPosition> obstacles,List<MapLayer> layers){public PlayerCombatMapView{tokens=List.copyOf(tokens);obstacles=Set.copyOf(obstacles);layers=List.copyOf(layers);if(layers.stream().anyMatch(l->l.visibility()!=LayerVisibility.PLAYER_VISIBLE))throw new IllegalArgumentException("player view contains AI_ONLY layer");}}
