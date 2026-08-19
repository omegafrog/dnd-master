package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovePlayerTokenCommand;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import com.dndmaster.combatmap.application.view.CombatMapAccessDeniedException;
import com.dndmaster.combatmap.application.view.UploadedMapSource;
import com.dndmaster.combatmap.application.view.TacticalSceneMaterialization;
import com.dndmaster.combatmap.application.view.TacticalTriggerEffect;
import com.dndmaster.combatmap.domain.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class CombatMapController {
    private final CombatMapViewService mapViewService;
    private final CombatMapMovementService movementService;
    private final ApiRequestGuard requestGuard;

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard) {
        this.mapViewService = mapViewService;
        this.movementService = movementService;
        this.requestGuard = requestGuard;
    }

    @GetMapping("/internal/v1/combat-maps/{mapId}/player-view")
    public PlayerCombatMapResponse playerView(
            @PathVariable UUID mapId, @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        PlayerCombatMapView view = mapViewService.displayForPlayer(new MapId(mapId), new MapOwnerId(ownerId));
        return PlayerCombatMapResponse.from(view);
    }

    @GetMapping("/internal/v1/combat-maps/{mapId}/gm-view")
    GmCombatMapResponse gmView(@PathVariable UUID mapId, @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        return GmCombatMapResponse.from(mapViewService.displayForGm(new MapId(mapId), new MapOwnerId(ownerId)));
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/tactical-triggers")
    public CombatMapAiStateResponse applyTacticalTrigger(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody TacticalTriggerRequest request) {
        requestGuard.internal(token);
        TacticalTriggerEffect.Kind kind;
        try { kind = TacticalTriggerEffect.Kind.valueOf(request.kind()); }
        catch (RuntimeException exception) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid tactical trigger kind", exception); }
        var map = mapViewService.applyTacticalTrigger(new MapId(mapId), new MapOwnerId(request.ownerId()), request.expectedVersion(),
                request.commandId(), TacticalTriggerEffect.planned(request.triggerId(),
                        kind, request.targetIds()));
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/prepare")
    public PrepareResponse prepare(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                            @RequestBody PrepareRequest request) {
        requestGuard.internal(token);
        CombatMap map = request.tacticalScene() == null
                ? mapViewService.prepareGenerated(new MapOwnerId(request.ownerId()), new AdventureId(request.adventureId()),
                        new RuleSetId(request.ruleSetId()), request.assetId() + "@" + request.assetLocator(), request.playerSpawnX(), request.playerSpawnY())
                : mapViewService.prepareTactical(new MapOwnerId(request.ownerId()), new AdventureId(request.adventureId()),
                        new RuleSetId(request.ruleSetId()), request.assetId() + "@" + request.assetLocator(), request.tacticalScene());
        return new PrepareResponse(map.id().value());
    }

    @PostMapping(value = "/internal/v1/combat-maps/prepare-upload", consumes = "multipart/form-data")
    public PrepareResponse prepareUpload(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                  @RequestPart MultipartFile file, @RequestParam UUID adventureId,
                                  @RequestParam UUID ownerId, @RequestParam UUID ruleSetId) throws java.io.IOException {
        requestGuard.internal(token);
        CombatMap map = mapViewService.prepareUploaded(new MapOwnerId(ownerId), new AdventureId(adventureId),
                new RuleSetId(ruleSetId), new UploadedMapSource(file.getOriginalFilename(), file.getBytes()));
        return new PrepareResponse(map.id().value());
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/combat-map/player-view")
    public PlayerCombatMapResponse playerAdventureView(@PathVariable UUID adventureId, @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        PlayerCombatMapView view = mapViewService.displayForAdventure(new AdventureId(adventureId), new MapOwnerId(ownerId))
                .orElseThrow(CombatMapAccessDeniedException::new);
        return PlayerCombatMapResponse.from(view);
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/moves")
    public CombatMapMoveResponse movePlayer(
            @PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody MoveRequest request) {
        requestGuard.internal(token);
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
    public CombatMapAiStateResponse controlAiState(
            @PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody AiStateRequest request) {
        requestGuard.internal(token);
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

    @PostMapping("/internal/v1/combat-maps/{mapId}/doors")
    public CombatMapAiStateResponse changeDoor(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody DoorRequest request) {
        requestGuard.internal(token);
        CombatMap map=mapViewService.changeDoor(new MapId(mapId),new MapOwnerId(request.ownerId()),request.expectedVersion(),request.commandId(),new GridPosition(request.x(),request.y()),request.open());
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/reveals")
    public CombatMapAiStateResponse reveal(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody RevealRequest request) {
        requestGuard.internal(token);
        CombatMap map=mapViewService.revealToken(new MapId(mapId),new MapOwnerId(request.ownerId()),request.expectedVersion(),request.commandId(),new TokenId(request.tokenId()));
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/game-time")
    public CombatMapAiStateResponse gameTime(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody GameTimeRequest request) {
        requestGuard.internal(token);
        CombatMap map=mapViewService.onGameTimeAdvanced(new MapId(mapId),new MapOwnerId(request.ownerId()),request.expectedVersion(),new GameTimeAdvanced(request.adventureId(),request.ruleTurn(),request.causeId()));
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
    public record DoorRequest(UUID ownerId,int x,int y,boolean open,UUID commandId,long expectedVersion) {}
    public record RevealRequest(UUID ownerId,UUID tokenId,UUID commandId,long expectedVersion) {}
    public record GameTimeRequest(UUID ownerId,UUID adventureId,long ruleTurn,UUID causeId,long expectedVersion) {}
    public record TacticalTriggerRequest(UUID ownerId, UUID commandId, long expectedVersion,
                                         String triggerId, String kind, List<String> targetIds) {}

    public record LayerRequest(String type, String value, String visibility) {}

    public record CombatMapMoveResponse(UUID mapId) {}

    public record CombatMapAiStateResponse(UUID mapId) {}

    public record PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId,
                                 UUID mapDefinitionId, String assetId, String assetLocator,
                                 Integer playerSpawnX, Integer playerSpawnY, TacticalSceneMaterialization tacticalScene) {
        public PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId, String assetId,
                String assetLocator, Integer playerSpawnX, Integer playerSpawnY) {
            this(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, playerSpawnX, playerSpawnY, null);
        }
        public PrepareRequest { playerSpawnX = playerSpawnX == null ? 0 : playerSpawnX; playerSpawnY = playerSpawnY == null ? 0 : playerSpawnY; }
    }
    public record PrepareResponse(UUID mapId) {}
}
