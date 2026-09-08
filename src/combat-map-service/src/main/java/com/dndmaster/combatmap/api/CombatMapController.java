package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovePlayerTokenCommand;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import com.dndmaster.combatmap.application.view.CombatMapAccessDeniedException;
import com.dndmaster.combatmap.application.view.MapActivationContext;
import com.dndmaster.combatmap.application.view.MapGenerationRequest;
import com.dndmaster.combatmap.application.view.UploadedMapSource;
import com.dndmaster.combatmap.application.view.TacticalSceneMaterialization;
import com.dndmaster.combatmap.application.view.TacticalTriggerEffect;
import com.dndmaster.combatmap.domain.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;

@RestController
@RequestMapping
public class CombatMapController {
    private final CombatMapViewService mapViewService;
    private final CombatMapMovementService movementService;
    private final ApiRequestGuard requestGuard;
    private final com.dndmaster.combatmap.application.view.MapImageEvidencePort mapImageEvidence;
    private final com.dndmaster.combatmap.application.view.MapGridAlignmentService mapGridAlignmentService;

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard) {
        this(mapViewService, movementService, requestGuard, (documentId, locator) -> java.util.Optional.empty());
    }

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard,
            com.dndmaster.combatmap.application.view.MapImageEvidencePort mapImageEvidence) {
        this(mapViewService, movementService, requestGuard, mapImageEvidence, null);
    }

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard,
            com.dndmaster.combatmap.application.view.MapImageEvidencePort mapImageEvidence,
            com.dndmaster.combatmap.application.view.MapGridAlignmentService mapGridAlignmentService) {
        this.mapViewService = mapViewService;
        this.movementService = movementService;
        this.requestGuard = requestGuard;
        this.mapImageEvidence = mapImageEvidence;
        this.mapGridAlignmentService = mapGridAlignmentService;
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
            @RequestBody(required = false) TacticalTriggerRequest request) {
        requestGuard.internal(token);
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "trigger request is required");
        }
        if (request.qualifyingAction() == null || request.qualifyingAction().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "qualifyingAction is required");
        }
        TacticalTriggerEffect.Kind kind;
        try { kind = TacticalTriggerEffect.Kind.valueOf(request.kind()); }
        catch (RuntimeException exception) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid tactical trigger kind", exception); }
        var map = mapViewService.applyTacticalTrigger(new MapId(mapId), new MapOwnerId(request.ownerId()), request.expectedVersion(),
                request.commandId(), TacticalTriggerEffect.planned(request.triggerId(),
                        kind, request.targetIds(), request.transitionId(), request.qualifyingAction()));
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/prepare")
    public PrepareResponse prepare(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                            @RequestBody(required = false) PrepareRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "prepare request is required");
        if (request.stagePosition() != null) {
            var existing = mapViewService.displayForAdventure(new AdventureId(request.adventureId()), new MapOwnerId(request.ownerId()));
            if (existing.isPresent()) return new PrepareResponse(existing.get().mapId().value());
        }
        Set<GridPosition> authoredObstacles = authoredPositions(request.obstacles(), "obstacles");
        authoredObstacles.addAll(authoredPositions(request.walls(), "walls"));
        List<Door> authoredDoors = authoredPositions(request.doors(), "doors").stream()
                .map(position -> new Door(position, false)).toList();
        var mapImage = request.sourceDocumentId() == null ? java.util.Optional.<com.dndmaster.combatmap.application.view.MapImageEvidence>empty()
                : mapImageEvidence.load(request.sourceDocumentId(), request.sourceAssetLocator());
        CombatMap map = request.tacticalScene() == null
                ? mapViewService.prepareGenerated(new MapOwnerId(request.ownerId()), new AdventureId(request.adventureId()),
                        new RuleSetId(request.ruleSetId()), new MapGenerationRequest(
                                request.assetId() + "@" + request.assetLocator(),
                                "scene=" + request.currentScene() + ";location=" + request.location()
                                        + ";entrySide=" + request.entrySide(),
                                20, 20, 30, 5, authoredObstacles, authoredDoors,
                                request.playerSpawnX() == null || request.playerSpawnY() == null ? null
                                        : new GridPosition(request.playerSpawnX(), request.playerSpawnY()),
                                mapImage.orElse(null)))
                : request.sourceImage() != null && !request.sourceImage().isBlank()
                ? mapViewService.prepareTactical(new MapOwnerId(request.ownerId()), new AdventureId(request.adventureId()),
                        new RuleSetId(request.ruleSetId()), request.assetId() + "@" + request.assetLocator(),
                        new UploadedMapSource(request.assetId() + (request.sourceImageContentType() != null && request.sourceImageContentType().contains("jpeg") ? ".jpg" : ".png"),
                                Base64.getDecoder().decode(request.sourceImage())), request.tacticalScene())
                : mapViewService.prepareTactical(new MapOwnerId(request.ownerId()), new AdventureId(request.adventureId()),
                        new RuleSetId(request.ruleSetId()), request.assetId() + "@" + request.assetLocator(), request.tacticalScene());
        if (request.stagePosition() != null) {
            java.util.Optional<GridPosition> candidate = request.playerSpawnX() == null || request.playerSpawnY() == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(new GridPosition(request.playerSpawnX(), request.playerSpawnY()));
            java.util.Optional<MapActivationContext.EntrySide> entrySide;
            try {
                entrySide = request.entrySide() == null || request.entrySide().isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(MapActivationContext.EntrySide.valueOf(request.entrySide().trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid entry side", exception);
            }
            mapViewService.activateForAdventure(map.id(), new MapOwnerId(request.ownerId()),
                    MapActivationContext.from(request.stagePosition(), candidate, entrySide,
                            java.util.Optional.ofNullable(request.playerTokenId()), request.situationId(), request.situationRevision(),
                            request.turnIndex(), request.currentScene(), request.location()));
        }
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

    @GetMapping("/internal/v1/combat-maps/{mapId}/alignment")
    public MapGridAlignmentResponse alignment(@PathVariable UUID mapId, @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        return MapGridAlignmentResponse.from(requireAlignmentService().find(new MapId(mapId), new MapOwnerId(ownerId)));
    }

    @PutMapping("/internal/v1/combat-maps/{mapId}/alignment")
    public MapGridAlignmentResponse applyAlignment(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) MapGridAlignmentRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "map grid alignment request is required");
        try {
            return MapGridAlignmentResponse.from(requireAlignmentService().apply(new MapId(mapId), new MapOwnerId(request.ownerId()),
                    new com.dndmaster.combatmap.application.view.MapGridAlignmentRequest(request.commandId(), request.expectedVersion(),
                            request.imageRevision(), request.originX(), request.originY(), request.cellSize())));
        } catch (com.dndmaster.combatmap.application.view.MapGridAlignmentConflictException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public CombatMapMoveResponse movePlayer(UUID mapId, String token, MoveRequest request) {
        return movePlayerInternal(mapId, token, request == null ? null : request.commandId().toString(), request);
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/moves")
    public CombatMapMoveResponse movePlayer(
            @PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) MoveRequest request) {
        return movePlayerInternal(mapId, token, idempotencyKey, request);
    }

    private CombatMapMoveResponse movePlayerInternal(UUID mapId, String token, String idempotencyKey, MoveRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "move request is required");
        requireIdempotencyKey(idempotencyKey, request.commandId());
        if (request.positions() == null || request.positions().size() < 2) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "movement path requires a destination");
        }
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
        return new CombatMapMoveResponse(map.id().value(), map.version());
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/ai-state")
    public CombatMapAiStateResponse controlAiState(
            @PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) AiStateRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "AI state request is required");
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
    public CombatMapAiStateResponse changeDoor(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody(required = false) DoorRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "door request is required");
        CombatMap map=mapViewService.changeDoor(new MapId(mapId),new MapOwnerId(request.ownerId()),request.expectedVersion(),request.commandId(),new GridPosition(request.x(),request.y()),request.open());
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PutMapping("/internal/v1/combat-maps/{mapId}/calibration")
    public CombatMapAiStateResponse calibrateGrid(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) GridCalibrationRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "grid calibration request is required");
        CombatMap map = mapViewService.calibrateGrid(new MapId(mapId), new MapOwnerId(request.ownerId()), request.expectedVersion(),
                new com.dndmaster.combatmap.application.view.GridCalibrationRequest(request.width(), request.height(), request.cellSize(),
                        request.originX(), request.originY(), request.imageWidth(), request.imageHeight(), request.playerX(), request.playerY()));
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/reveals")
    public CombatMapAiStateResponse reveal(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody(required = false) RevealRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "reveal request is required");
        CombatMap map=mapViewService.revealToken(new MapId(mapId),new MapOwnerId(request.ownerId()),request.expectedVersion(),request.commandId(),new TokenId(request.tokenId()));
        return new CombatMapAiStateResponse(map.id().value());
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/game-time")
    public CombatMapAiStateResponse gameTime(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody(required = false) GameTimeRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "game-time request is required");
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
    public record GridCalibrationRequest(UUID ownerId, long expectedVersion, int width, int height, int cellSize,
                                         int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {}
    public record MapGridAlignmentRequest(UUID ownerId, UUID commandId, long expectedVersion, String imageRevision,
                                          double originX, double originY, double cellSize) {}
    public record MapGridAlignmentResponse(UUID mapId, long version, String imageRevision, double originX, double originY, double cellSize) {
        static MapGridAlignmentResponse from(com.dndmaster.combatmap.application.view.MapGridAlignment alignment) {
            return new MapGridAlignmentResponse(alignment.mapId().value(), alignment.version(), alignment.imageRevision(), alignment.originX(), alignment.originY(), alignment.cellSize());
        }
    }
    public record RevealRequest(UUID ownerId,UUID tokenId,UUID commandId,long expectedVersion) {}
    public record GameTimeRequest(UUID ownerId,UUID adventureId,long ruleTurn,UUID causeId,long expectedVersion) {}

    private static void requireRequest(Object request, String message) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
        }
    }
    private com.dndmaster.combatmap.application.view.MapGridAlignmentService requireAlignmentService() {
        if (mapGridAlignmentService == null) throw new IllegalStateException("map grid alignment unavailable");
        return mapGridAlignmentService;
    }

    private static void requireIdempotencyKey(String header, UUID commandId) {
        if (header == null || header.isBlank() || commandId == null || !header.equals(commandId.toString())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must match commandId");
        }
    }
    public record TacticalTriggerRequest(UUID ownerId, UUID commandId, long expectedVersion,
                                         String triggerId, String kind, List<String> targetIds, String transitionId, String qualifyingAction) {
        public TacticalTriggerRequest(UUID ownerId, UUID commandId, long expectedVersion, String triggerId, String kind, List<String> targetIds) {
            this(ownerId, commandId, expectedVersion, triggerId, kind, targetIds, "", "");
        }
        public TacticalTriggerRequest(UUID ownerId, UUID commandId, long expectedVersion, String triggerId, String kind, List<String> targetIds, String transitionId) {
            this(ownerId, commandId, expectedVersion, triggerId, kind, targetIds, transitionId, "");
        }
    }

    public record LayerRequest(String type, String value, String visibility) {}

    public record CombatMapMoveResponse(UUID mapId, long version) {
        public CombatMapMoveResponse(UUID mapId) { this(mapId, 0); }
    }

    public record CombatMapAiStateResponse(UUID mapId) {}

    public record PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId,
                                 UUID mapDefinitionId, String assetId, String assetLocator,
                                 Integer playerSpawnX, Integer playerSpawnY, String sourceImage, String sourceImageContentType,
                                 TacticalSceneMaterialization tacticalScene, Integer stagePosition,
                                 UUID playerTokenId, UUID situationId, Long situationRevision, Integer turnIndex,
                                 String currentScene, String location, String entrySide,
                                 List<String> walls, List<String> doors, List<String> obstacles,
                                 UUID sourceDocumentId, String sourceAssetLocator) {
        public PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId, String assetId,
                String assetLocator, Integer playerSpawnX, Integer playerSpawnY) {
            this(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, playerSpawnX, playerSpawnY,
                    null, null, null, null, null, UUID.randomUUID(), 1L, 0, "unknown", "unknown", null,
                    List.of(), List.of(), List.of(), null, null);
        }
        public PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId, String assetId,
                String assetLocator, Integer playerSpawnX, Integer playerSpawnY, String sourceImage,
                String sourceImageContentType, TacticalSceneMaterialization tacticalScene, Integer stagePosition) {
            this(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, playerSpawnX, playerSpawnY,
                    sourceImage, sourceImageContentType, tacticalScene, stagePosition, null, UUID.randomUUID(), 1L, 0,
                    "unknown", "unknown", null, List.of(), List.of(), List.of(), null, null);
        }
        public PrepareRequest {
            if (stagePosition != null && stagePosition < 1) throw new IllegalArgumentException("stage position must be positive");
            if (situationId == null) situationId = UUID.randomUUID();
            if (situationRevision == null) situationRevision = 1L;
            if (situationRevision < 1) throw new IllegalArgumentException("situation revision must be positive");
            if (turnIndex == null) turnIndex = 0;
            if (turnIndex < 0) throw new IllegalArgumentException("turn index must not be negative");
            currentScene = currentScene == null || currentScene.isBlank() ? "unknown" : currentScene.trim();
            location = location == null || location.isBlank() ? "unknown" : location.trim();
            walls = walls == null ? List.of() : List.copyOf(walls);
            doors = doors == null ? List.of() : List.copyOf(doors);
            obstacles = obstacles == null ? List.of() : List.copyOf(obstacles);
            sourceAssetLocator = sourceAssetLocator == null ? "" : sourceAssetLocator.trim();
        }
    }

    private static Set<GridPosition> authoredPositions(List<String> values, String label) {
        Set<GridPosition> result = new java.util.HashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null || value.isBlank()) continue;
            String[] pair = value.trim().split("[, :]", -1);
            if (pair.length != 2) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid " + label + " position");
            try { result.add(new GridPosition(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]))); }
            catch (NumberFormatException exception) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid " + label + " position", exception); }
        }
        return result;
    }
    public record PrepareResponse(UUID mapId) {}
}
