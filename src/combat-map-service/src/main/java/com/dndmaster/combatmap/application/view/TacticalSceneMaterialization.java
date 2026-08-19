package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenDiscovery;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Transport-neutral tactical scene data materialized only after a map grid is available. */
public record TacticalSceneMaterialization(List<Placement> placements, List<Environment> environments,
        List<Position> hiddenRegions) {
    public TacticalSceneMaterialization {
        placements = List.copyOf(Objects.requireNonNull(placements, "tactical placements must be explicit"));
        environments = List.copyOf(Objects.requireNonNull(environments, "tactical environments must be explicit"));
        hiddenRegions = List.copyOf(Objects.requireNonNull(hiddenRegions, "tactical hidden regions must be explicit"));
    }

    public PreparedMapData materialize(GridSpec grid, UUID ownerPlayerId) {
        Set<GridPosition> occupied = new HashSet<>();
        List<CombatToken> tokens = new ArrayList<>();
        for (Placement placement : placements) {
            GridPosition position = gridPosition(grid, placement.x(), placement.y());
            if (!occupied.add(position)) throw new IllegalArgumentException("tactical placements collide after grid conversion");
            TokenType type = TokenType.valueOf(placement.kind());
            boolean player = type == TokenType.PLAYER;
            tokens.add(new CombatToken(new TokenId(UUID.nameUUIDFromBytes(placement.id().getBytes(java.nio.charset.StandardCharsets.UTF_8))), type,
                    position, player ? TokenController.PLAYER : TokenController.AI_GAME_MASTER,
                    player ? new PlayerId(ownerPlayerId) : null, player ? TokenDiscovery.REVEALED : TokenDiscovery.HIDDEN));
        }
        if (tokens.isEmpty()) throw new IllegalArgumentException("ready tactical scene must place at least one token");
        Set<GridPosition> obstacles = new HashSet<>();
        for (Environment environment : environments) {
            GridPosition position = gridPosition(grid, environment.x(), environment.y());
            if (occupied.contains(position) || !obstacles.add(position)) throw new IllegalArgumentException("tactical environment collides after grid conversion");
        }
        List<String> fog = hiddenRegions.stream().map(point -> gridPosition(grid, point.x(), point.y()))
                .map(point -> point.x() + "," + point.y()).toList();
        List<MapLayer> layers = fog.isEmpty() ? List.of() : List.of(new MapLayer("INITIAL_FOG", String.join(";", fog), LayerVisibility.AI_ONLY));
        return new PreparedMapData(grid, tokens, obstacles, layers);
    }

    private static GridPosition gridPosition(GridSpec grid, double x, double y) {
        if (x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("normalized tactical coordinate is outside range");
        return new GridPosition((int) Math.round(x * (grid.width() - 1)), (int) Math.round(y * (grid.height() - 1)));
    }

    public record Placement(String id, String kind, double x, double y) {
        public Placement {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("tactical placement id required");
            TokenType.valueOf(kind);
        }
    }
    public record Environment(String id, String kind, double x, double y) {
        public Environment { if (id == null || id.isBlank() || kind == null || kind.isBlank()) throw new IllegalArgumentException("tactical environment fields required"); }
    }
    public record Position(double x, double y) { }
}
