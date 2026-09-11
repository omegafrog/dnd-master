package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import com.dndmaster.combatmap.domain.LayerVisibility;
import java.util.List;
import java.util.UUID;

public record PlayerCombatMapResponse(UUID mapId, GridResponse grid, List<TokenResponse> tokens, List<ObstacleResponse> obstacles, List<DoorResponse> doors, List<LayerResponse> layers, List<PositionResponse> current, List<PositionResponse> explored, long version, List<PlayerStartResponse> playerStartCandidates) {
    public static PlayerCombatMapResponse from(PlayerCombatMapView v) {
        return new PlayerCombatMapResponse(
                v.mapId().value(), new GridResponse(v.grid().width(), v.grid().height(), v.grid().cellSize(), v.grid().distanceUnit()),
                v.tokens().stream()
                        .map(t -> new TokenResponse(t.id().value(), t.type().name(), t.position().x(), t.position().y(), v.lastSeenTokens().contains(t.id())))
                        .toList(),
                v.obstacles().stream().map(p -> new ObstacleResponse(p.x(), p.y())).toList(),
                v.doors().stream().map(d -> new DoorResponse(d.position().x(), d.position().y(), d.open())).toList(),
                v.layers().stream().map(l -> new LayerResponse(l.type(), l.value(), LayerVisibility.PLAYER_VISIBLE.name())).toList(),
                v.current().stream().map(p -> new PositionResponse(p.x(), p.y())).toList(),
                v.explored().stream().map(p -> new PositionResponse(p.x(), p.y())).toList(),
                v.version(), v.playerStartCandidates().stream().map(PlayerStartResponse::from).toList());
    }
    public PlayerCombatMapResponse(UUID mapId, GridResponse grid, List<TokenResponse> tokens, List<ObstacleResponse> obstacles, List<DoorResponse> doors, List<LayerResponse> layers, List<PositionResponse> current, List<PositionResponse> explored, long version) {
        this(mapId, grid, tokens, obstacles, doors, layers, current, explored, version, List.of());
    }

    public record GridResponse(int width, int height, int cellSize, int distanceUnit) {}
    public record ObstacleResponse(int x, int y) {}
    public record DoorResponse(int x, int y, boolean open) {}
    public record TokenResponse(UUID id, String type, int x, int y, boolean lastSeen) {}
    public record PositionResponse(int x, int y) {}
    public record LayerResponse(String type, String value, String visibility) {}
    public record PlayerStartResponse(int x, int y, double confidence, List<String> evidence, String source) {
        static PlayerStartResponse from(com.dndmaster.combatmap.application.view.PlayerStartCandidate candidate) {
            return new PlayerStartResponse(candidate.position().x(), candidate.position().y(), candidate.confidence(), candidate.evidence(), candidate.source());
        }
    }
}
