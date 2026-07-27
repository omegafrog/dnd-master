package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovePlayerTokenCommand;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import com.dndmaster.combatmap.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class CombatMapController {
    private final CombatMapViewService mapViewService;
    private final CombatMapMovementService movementService;

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService) {
        this.mapViewService = mapViewService;
        this.movementService = movementService;
    }

    @GetMapping("/internal/v1/combat-maps/{mapId}/player-view")
    PlayerCombatMapResponse playerView(
            @PathVariable UUID mapId, @RequestParam UUID ownerId) {
        PlayerCombatMapView view = mapViewService.displayForPlayer(new MapId(mapId), new MapOwnerId(ownerId));
        return PlayerCombatMapResponse.from(view);
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/moves")
    CombatMapMoveResponse movePlayer(
            @PathVariable UUID mapId, @RequestBody MoveRequest request) {
        MovementPath path = new MovementPath(
                request.positions().stream().map(p -> new GridPosition(p.x(), p.y())).toList(),
                request.distance());
        MovePlayerTokenCommand command = new MovePlayerTokenCommand(
                new MapId(mapId),
                new PlayerId(request.playerId()),
                new TokenId(request.tokenId()),
                path,
                request.appliedEdition(),
                request.commandId(),
                request.expectedVersion());
        CombatMap map = movementService.movePlayerToken(command);
        return new CombatMapMoveResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/ai-state")
    CombatMapAiStateResponse controlAiState(
            @PathVariable UUID mapId, @RequestBody AiStateRequest request) {
        GridPosition position = new GridPosition(request.x(), request.y());
        List<MapLayer> aiLayers = request.layers() == null ? List.of() :
                request.layers().stream()
                        .map(l -> new MapLayer(l.type(), l.value(), LayerVisibility.valueOf(l.visibility())))
                        .toList();
        CombatMap map = mapViewService.controlAiState(
                new MapId(mapId), new MapOwnerId(request.ownerId()),
                request.expectedVersion(), request.commandId(), new TokenId(request.tokenId()),
                position, aiLayers);
        return new CombatMapAiStateResponse(map.id().value());
    }

    public record MoveRequest(
            UUID playerId, UUID tokenId,
            List<PositionRequest> positions, int distance,
            String appliedEdition, UUID commandId, long expectedVersion) {}

    public record PositionRequest(int x, int y) {}

    public record AiStateRequest(
            UUID ownerId, UUID tokenId,
            int x, int y,
            UUID commandId,
            long expectedVersion,
            List<LayerRequest> layers) {}

    public record LayerRequest(String type, String value, String visibility) {}

    public record CombatMapMoveResponse(UUID mapId) {}

    public record CombatMapAiStateResponse(UUID mapId) {}
}
