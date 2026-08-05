package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import java.util.List;
import java.util.UUID;

public record PlayerCombatMapResponse(UUID mapId, GridResponse grid, List<TokenResponse> tokens, List<ObstacleResponse> obstacles, List<LayerResponse> layers, long version) {
    public static PlayerCombatMapResponse from(PlayerCombatMapView v) {
        return new PlayerCombatMapResponse(
                v.mapId().value(), new GridResponse(v.grid().width(), v.grid().height(), v.grid().cellSize(), v.grid().distanceUnit()),
                v.tokens().stream()
                        .map(t -> new TokenResponse(t.id().value(), t.type().name(), t.position().x(), t.position().y()))
                        .toList(),
                v.obstacles().stream().map(p -> new ObstacleResponse(p.x(), p.y())).toList(),
                v.layers().stream().map(l -> new LayerResponse(l.type(), l.value())).toList(),
                v.version());
    }

    public record GridResponse(int width, int height, int cellSize, int distanceUnit) {}
    public record ObstacleResponse(int x, int y) {}
    public record TokenResponse(UUID id, String type, int x, int y) {}
    public record LayerResponse(String type, String value) {}
}
