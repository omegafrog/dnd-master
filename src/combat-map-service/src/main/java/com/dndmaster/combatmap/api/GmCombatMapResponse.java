package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.domain.CombatMap;
import java.util.List;
import java.util.UUID;

/** Internal/GM-only projection; it deliberately retains hidden runtime state. */
public record GmCombatMapResponse(UUID mapId, List<TokenResponse> tokens, List<ObstacleResponse> obstacles,
        List<LayerResponse> layers, long version, List<SpatialFeatureResponse> spatialFeatures,
        boolean spatialPreparationBlocked) {
    public GmCombatMapResponse(UUID mapId, List<TokenResponse> tokens, List<ObstacleResponse> obstacles,
            List<LayerResponse> layers, long version) {
        this(mapId, tokens, obstacles, layers, version, List.of(), false);
    }
    public static GmCombatMapResponse from(CombatMap map) {
        return new GmCombatMapResponse(map.id().value(), map.tokens().stream()
                .map(token -> new TokenResponse(token.id().value(), token.type().name(), token.position().x(), token.position().y(), token.discovery().name()))
                .toList(), map.obstacles().stream().map(point -> new ObstacleResponse(point.x(), point.y())).toList(),
                map.layers().stream().map(layer -> new LayerResponse(layer.type(), layer.value(), layer.visibility().name())).toList(), map.version(),
                map.spatialFeatures().stream().map(feature -> SpatialFeatureResponse.from(feature)).toList(), map.spatialPreparationBlocked());
    }
    public record TokenResponse(UUID id, String type, int x, int y, String discovery) { }
    public record ObstacleResponse(int x, int y) { }
    public record LayerResponse(String type, String value, String visibility) { }
    public record PositionResponse(int x, int y) { }
    public record SpatialFeatureResponse(UUID id, String type, List<PositionResponse> cells,
            String visibility, String state, String origin, String sourceReference) {
        static SpatialFeatureResponse from(com.dndmaster.combatmap.domain.SpatialFeature feature) {
            return new SpatialFeatureResponse(feature.id(), feature.type().name(),
                    feature.cells().stream().map(cell -> new PositionResponse(cell.x(), cell.y())).toList(),
                    feature.visibility().name(), feature.state().name(), feature.provenance().origin().name(),
                    feature.provenance().sourceReference());
        }
    }
}
