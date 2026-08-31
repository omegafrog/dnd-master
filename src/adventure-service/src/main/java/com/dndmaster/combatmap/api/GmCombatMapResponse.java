package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.domain.CombatMap;
import java.util.List;
import java.util.UUID;

/** Internal/GM-only projection; it deliberately retains hidden runtime state. */
public record GmCombatMapResponse(UUID mapId, List<TokenResponse> tokens, List<ObstacleResponse> obstacles,
        List<LayerResponse> layers, long version) {
    public static GmCombatMapResponse from(CombatMap map) {
        return new GmCombatMapResponse(map.id().value(), map.tokens().stream()
                .map(token -> new TokenResponse(token.id().value(), token.type().name(), token.position().x(), token.position().y(), token.discovery().name()))
                .toList(), map.obstacles().stream().map(point -> new ObstacleResponse(point.x(), point.y())).toList(),
                map.layers().stream().map(layer -> new LayerResponse(layer.type(), layer.value(), layer.visibility().name())).toList(), map.version());
    }
    public record TokenResponse(UUID id, String type, int x, int y, String discovery) { }
    public record ObstacleResponse(int x, int y) { }
    public record LayerResponse(String type, String value, String visibility) { }
}
