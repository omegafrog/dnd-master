package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.*;

public record PlayerCombatMapView(
        MapId mapId, GridSpec grid, List<CombatToken> tokens, Set<GridPosition> obstacles, List<Door> doors,
        List<MapLayer> layers, Set<GridPosition> current, Set<GridPosition> explored, Set<TokenId> lastSeenTokens,
        long version, List<PlayerStartCandidate> playerStartCandidates, List<SpatialFeature> spatialFeatures) {
    public PlayerCombatMapView {
        tokens = List.copyOf(tokens); obstacles = Set.copyOf(obstacles); doors = List.copyOf(doors);
        layers = List.copyOf(layers); current = Set.copyOf(current); explored = Set.copyOf(explored);
        lastSeenTokens = Set.copyOf(lastSeenTokens);
        playerStartCandidates = playerStartCandidates == null ? List.of() : List.copyOf(playerStartCandidates);
        spatialFeatures = spatialFeatures == null ? List.of() : List.copyOf(spatialFeatures);
        if (layers.stream().anyMatch(l -> l.visibility() != LayerVisibility.PLAYER_VISIBLE)) {
            throw new IllegalArgumentException("player view contains AI_ONLY layer");
        }
        if (!explored.containsAll(current) || version < 0) throw new IllegalArgumentException("invalid player visibility");
    }

    public PlayerCombatMapView(MapId mapId, GridSpec grid, List<CombatToken> tokens, Set<GridPosition> obstacles,
            List<Door> doors, List<MapLayer> layers, Set<GridPosition> current, Set<GridPosition> explored,
            Set<TokenId> lastSeenTokens, long version, List<PlayerStartCandidate> playerStartCandidates) {
        this(mapId, grid, tokens, obstacles, doors, layers, current, explored, lastSeenTokens, version,
                playerStartCandidates, List.of());
    }

    public PlayerCombatMapView(MapId mapId, GridSpec grid, List<CombatToken> tokens, Set<GridPosition> obstacles,
            List<Door> doors, List<MapLayer> layers, Set<GridPosition> current, Set<GridPosition> explored,
            Set<TokenId> lastSeenTokens, long version) {
        this(mapId, grid, tokens, obstacles, doors, layers, current, explored, lastSeenTokens, version, List.of());
    }

    public record SpatialFeature(UUID id, String type, List<Position> cells, String visibility, String state,
            boolean interactable) {
        public SpatialFeature { cells = List.copyOf(cells); }
    }

    public record Position(int x, int y) {}
}
