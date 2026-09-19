package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovePlayerTokenCommand;
import com.dndmaster.combatmap.application.movement.MovementPreview;
import com.dndmaster.combatmap.application.movement.MovementPreviewRequest;
import com.dndmaster.combatmap.application.movement.MovementOperationResponse;
import com.dndmaster.combatmap.application.movement.MovementStartRequest;
import com.dndmaster.combatmap.application.movement.MovementCheckResultBody;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import com.dndmaster.combatmap.application.view.CombatMapAccessDeniedException;
import com.dndmaster.combatmap.application.view.MapActivationContext;
import com.dndmaster.combatmap.application.view.MapGenerationRequest;
import com.dndmaster.combatmap.application.view.UploadedMapSource;
import com.dndmaster.combatmap.application.view.TacticalSceneMaterialization;
import com.dndmaster.combatmap.application.view.TacticalTriggerEffect;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePlacementBatch;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommand;
import com.dndmaster.combatmap.application.spatial.SpatialFeatureApplicationService;
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
    private final com.dndmaster.combatmap.application.view.PublicMapImageArtifactService publicMapImages;
    private final com.dndmaster.combatmap.application.view.MapFilePreparationPort mapFilePreparation;
    private final com.dndmaster.combatmap.application.spatial.SpatialFeatureRuntimeApplicationService spatialRuntime;

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
        this(mapViewService, movementService, requestGuard, mapImageEvidence, mapGridAlignmentService, null);
    }

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard,
            com.dndmaster.combatmap.application.view.MapImageEvidencePort mapImageEvidence,
            com.dndmaster.combatmap.application.view.MapGridAlignmentService mapGridAlignmentService,
            com.dndmaster.combatmap.application.view.PublicMapImageArtifactService publicMapImages) {
        this(mapViewService, movementService, requestGuard, mapImageEvidence, mapGridAlignmentService, publicMapImages, null);
    }

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard,
            com.dndmaster.combatmap.application.view.MapImageEvidencePort mapImageEvidence,
            com.dndmaster.combatmap.application.view.MapGridAlignmentService mapGridAlignmentService,
            com.dndmaster.combatmap.application.view.PublicMapImageArtifactService publicMapImages,
            com.dndmaster.combatmap.application.view.MapFilePreparationPort mapFilePreparation) {
        this(mapViewService, movementService, requestGuard, mapImageEvidence, mapGridAlignmentService,
                publicMapImages, mapFilePreparation, null);
    }

    public CombatMapController(CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard,
            com.dndmaster.combatmap.application.view.MapImageEvidencePort mapImageEvidence,
            com.dndmaster.combatmap.application.view.MapGridAlignmentService mapGridAlignmentService,
            com.dndmaster.combatmap.application.view.PublicMapImageArtifactService publicMapImages,
            com.dndmaster.combatmap.application.view.MapFilePreparationPort mapFilePreparation,
            com.dndmaster.combatmap.application.spatial.SpatialFeatureRuntimeApplicationService spatialRuntime) {
        this.mapViewService = mapViewService;
        this.movementService = movementService;
        this.requestGuard = requestGuard;
        this.mapImageEvidence = mapImageEvidence;
        this.mapGridAlignmentService = mapGridAlignmentService;
        this.publicMapImages = publicMapImages;
        this.mapFilePreparation = mapFilePreparation;
        this.spatialRuntime = spatialRuntime;
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
                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                            @RequestBody(required = false) PrepareRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "prepare request is required");
        requireIdempotencyKey(idempotencyKey, request.commandId());
        if (request.stagePosition() != null && request.mapDefinitionId() == null) {
            if (mapViewService.preparedMapIdForAdventure(new AdventureId(request.adventureId()), new MapOwnerId(request.ownerId())).isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "reviewed combat map draft not found");
            }
            return activatePreparedMap(request);
        }
        SpatialPreparationCommand preparationCommand = new SpatialPreparationCommand(
                request.commandId(), request.operationFingerprint(), request.expectedVersion());
        var preparationOwner = new MapOwnerId(request.ownerId());
        var preparationAdventure = new AdventureId(request.adventureId());
        var replay = mapViewService.replaySpatialPreparation(preparationAdventure, preparationOwner, preparationCommand);
        if (replay.isPresent()) return prepareResponse(replay.get());
        SpatialFeaturePlacementBatch preparationBatch = spatialBatch(request);
        Set<GridPosition> authoredObstacles = authoredPositions(request.obstacles(), "obstacles");
        authoredObstacles.addAll(authoredPositions(request.walls(), "walls"));
        List<Door> authoredDoors = authoredPositions(request.doors(), "doors").stream()
                .map(position -> new Door(position, false)).toList();
        var mapImage = request.sourceDocumentId() == null ? java.util.Optional.<com.dndmaster.combatmap.application.view.MapImageEvidence>empty()
                : mapImageEvidence.load(request.sourceDocumentId(), request.sourceAssetLocator());
        SpatialFeatureApplicationService.Result preparationResult = request.tacticalScene() == null
                ? mapViewService.prepareGenerated(preparationOwner, preparationAdventure,
                        new RuleSetId(request.ruleSetId()), generationRequest(request, authoredObstacles, authoredDoors, mapImage), true,
                        preparationBatch, request.turnIndex(), preparationCommand)
                : request.sourceImage() != null && !request.sourceImage().isBlank()
                ? mapViewService.prepareTactical(preparationOwner, preparationAdventure,
                        new RuleSetId(request.ruleSetId()), request.assetId() + "@" + request.assetLocator(),
                        new UploadedMapSource(request.assetId() + (request.sourceImageContentType() != null && request.sourceImageContentType().contains("jpeg") ? ".jpg" : ".png"),
                                Base64.getDecoder().decode(request.sourceImage())), request.tacticalScene(),
                        preparationBatch, request.turnIndex(), preparationCommand)
                : mapViewService.prepareTactical(preparationOwner, preparationAdventure,
                        new RuleSetId(request.ruleSetId()), request.assetId() + "@" + request.assetLocator(), request.tacticalScene(),
                        preparationBatch, request.turnIndex(), preparationCommand);
        PrepareResponse preparationResponse = prepareResponse(preparationResult);
        long preparedVersion = preparationResult.version();
        if (!preparationResult.activationAllowed()) return preparationResponse;
        if (request.stagePosition() != null) {
            java.util.Optional<GridPosition> candidate = request.playerSpawnX() == null || request.playerSpawnY() == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(new GridPosition(request.playerSpawnX(), request.playerSpawnY()));
            var activation = mapViewService.activatePreparedForAdventure(new AdventureId(request.adventureId()),
                    new MapOwnerId(request.ownerId()), preparedVersion, activationCommand(request, preparedVersion),
                    MapActivationContext.from(request.stagePosition(), candidate,
                            java.util.Optional.ofNullable(request.playerTokenId()), request.situationId(), request.situationRevision(),
                            request.turnIndex(), request.currentScene(), request.location(), request.entryEvidence()));
            if (!activation.activationAllowed()) return new PrepareResponse(activation.mapId().value(), PrepareStatus.BLOCKED, 0);
        }
        return preparationResponse;
    }

    @PostMapping("/internal/v1/combat-maps/preparation-replay")
    public PrepareResponse replayPreparation(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) ReplayRequest request) {
        requestGuard.internal(token);
        if (request == null || request.commandId() == null || request.adventureId() == null || request.ownerId() == null
                || request.commandFingerprint() == null || request.commandFingerprint().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "replay request is incomplete");
        }
        return mapViewService.replaySpatialPreparationByCommandId(new AdventureId(request.adventureId()), new MapOwnerId(request.ownerId()), request.commandId(), request.commandFingerprint())
                .map(CombatMapController::prepareResponse)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "preparation replay not found"));
    }

    private static PrepareResponse prepareResponse(SpatialFeatureApplicationService.Result result) {
        return new PrepareResponse(result.mapId().value(),
                result.status() == SpatialFeatureApplicationService.Status.BLOCKED ? PrepareStatus.BLOCKED : PrepareStatus.READY,
                result.warningCount());
    }

    private static SpatialPreparationCommand activationCommand(PrepareRequest request, long expectedVersion) {
        UUID commandId = UUID.nameUUIDFromBytes((request.commandId() + ":activation")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new SpatialPreparationCommand(commandId, request.operationFingerprint() + "|activation", expectedVersion);
    }

    /** Compatibility overload for direct unit callers; the HTTP contract requires the header. */
    public PrepareResponse prepare(String token, PrepareRequest request) {
        return prepare(token, request == null ? null : request.commandId().toString(), request);
    }

    /** 지도 이미지가 있으면 그 이미지에서 검출·보정한 격자를 생성 요청에 그대로 전달한다. */
    private MapGenerationRequest generationRequest(PrepareRequest request, Set<GridPosition> authoredObstacles,
            List<Door> authoredDoors, java.util.Optional<com.dndmaster.combatmap.application.view.MapImageEvidence> mapImage) {
        GridPosition playerStart = request.playerSpawnX() == null || request.playerSpawnY() == null ? null
                : new GridPosition(request.playerSpawnX(), request.playerSpawnY());
        if (mapImage.isPresent() && mapFilePreparation != null) {
            var prepared = mapFilePreparation.prepare(new UploadedMapSource(filenameFor(mapImage.get()), mapImage.get().content()));
            var normalizedImage = prepared.layers().stream().filter(layer -> layer.type().equals("MAP_IMAGE"))
                    .findFirst().map(layer -> imageEvidence(layer.value())).orElse(mapImage.get());
            var gridBounds = prepared.layers().stream().filter(layer -> layer.type().equals("GRID_BOUNDS"))
                    .map(com.dndmaster.combatmap.domain.MapLayer::value).findFirst().orElse("");
            double[] geometry = gridGeometry(gridBounds, prepared.grid().cellSize());
            return new MapGenerationRequest(request.assetId() + "@" + request.assetLocator(), requestContext(request),
                    prepared.grid().width(), prepared.grid().height(), prepared.grid().cellSize(), prepared.grid().distanceUnit(),
                    authoredObstacles, authoredDoors, playerStart, normalizedImage, geometry[0], geometry[1], geometry[2],
                    gridBounds, true);
        }
        return new MapGenerationRequest(request.assetId() + "@" + request.assetLocator(), requestContext(request),
                20, 20, 30, 5, authoredObstacles, authoredDoors, playerStart, mapImage.orElse(null));
    }

    private static String requestContext(PrepareRequest request) {
        return "scene=" + request.currentScene() + ";location=" + request.location() + ";entryEvidence=" + request.entryEvidence();
    }

    private static String filenameFor(com.dndmaster.combatmap.application.view.MapImageEvidence image) {
        return image.contentType().contains("jpeg") ? "map.jpg" : "map.png";
    }

    private static com.dndmaster.combatmap.application.view.MapImageEvidence imageEvidence(String dataUri) {
        int separator = dataUri.indexOf(',');
        if (separator < 0) throw new IllegalArgumentException("prepared map image is not a data URI");
        String metadata = dataUri.substring(0, separator);
        String contentType = metadata.startsWith("data:") ? metadata.substring(5, metadata.indexOf(';')) : "image/png";
        return new com.dndmaster.combatmap.application.view.MapImageEvidence(contentType,
                Base64.getDecoder().decode(dataUri.substring(separator + 1)));
    }

    private static double[] gridGeometry(String bounds, int fallbackCellSize) {
        String[] values = bounds.split(",", -1);
        if (values.length != 6) return new double[] {0, 0, fallbackCellSize};
        try {
            return new double[] {Double.parseDouble(values[0]), Double.parseDouble(values[1]), fallbackCellSize};
        } catch (NumberFormatException exception) {
            return new double[] {0, 0, fallbackCellSize};
        }
    }

    private PrepareResponse activatePreparedMap(PrepareRequest request) {
        java.util.Optional<GridPosition> candidate = request.playerSpawnX() == null || request.playerSpawnY() == null
                ? java.util.Optional.empty() : java.util.Optional.of(new GridPosition(request.playerSpawnX(), request.playerSpawnY()));
        var result = mapViewService.activatePreparedForAdventure(new AdventureId(request.adventureId()), new MapOwnerId(request.ownerId()),
                request.expectedVersion(), new SpatialPreparationCommand(request.commandId(), request.operationFingerprint(), request.expectedVersion()),
                MapActivationContext.from(
                request.stagePosition(), candidate, java.util.Optional.ofNullable(request.playerTokenId()),
                request.situationId(), request.situationRevision(), request.turnIndex(), request.currentScene(), request.location(), request.entryEvidence()));
        return new PrepareResponse(result.mapId().value(), result.status() == CombatMapViewService.PreparationStatus.BLOCKED
                ? PrepareStatus.BLOCKED : PrepareStatus.READY, 0);
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

    @GetMapping("/internal/v1/adventures/{adventureId}/combat-map/preparation-view")
    public PlayerCombatMapResponse preparationAdventureView(@PathVariable UUID adventureId, @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        return mapViewService.displayForPreparation(new AdventureId(adventureId), new MapOwnerId(ownerId))
                .map(PlayerCombatMapResponse::from).orElseThrow(CombatMapAccessDeniedException::new);
    }

    @GetMapping("/internal/v1/combat-maps/{mapId}/alignment")
    public MapGridAlignmentResponse alignment(@PathVariable UUID mapId, @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        MapId id = new MapId(mapId);
        MapOwnerId owner = new MapOwnerId(ownerId);
        String imageViewId = requirePublicMapImages().latestReference(id, owner).orElse("");
        return MapGridAlignmentResponse.from(requireAlignmentService().find(id, owner), imageViewId);
    }

    @GetMapping(value = "/internal/v1/combat-maps/{mapId}/alignment/image", produces = "image/png")
    public org.springframework.http.ResponseEntity<byte[]> alignmentImage(@PathVariable UUID mapId, @RequestParam UUID ownerId,
            @RequestParam String imageViewId, @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        byte[] png = requirePublicMapImages().download(new MapId(mapId), new MapOwnerId(ownerId), imageViewId)
                .map(com.dndmaster.combatmap.application.view.PublicMapImageArtifact::png)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(png);
    }

    @GetMapping(value = "/internal/v1/combat-maps/{mapId}/preparation-image", produces = "image/png")
    public org.springframework.http.ResponseEntity<byte[]> preparationImage(@PathVariable UUID mapId, @RequestParam UUID ownerId,
            @RequestParam(required = false) UUID sourceDocumentId, @RequestParam(required = false) String sourceAssetLocator,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        MapId id = new MapId(mapId);
        MapOwnerId owner = new MapOwnerId(ownerId);
        mapViewService.ensureSourceImage(id, owner, sourceDocumentId, sourceAssetLocator);
        byte[] png = requirePublicMapImages().sourcePng(id, owner)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(png);
    }

    @PutMapping("/internal/v1/combat-maps/{mapId}/alignment")
    public MapGridAlignmentResponse applyAlignment(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) MapGridAlignmentRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "map grid alignment request is required");
        try {
            MapId id = new MapId(mapId);
            MapOwnerId owner = new MapOwnerId(request.ownerId());
            var alignment = requireAlignmentService().apply(id, owner,
                    new com.dndmaster.combatmap.application.view.MapGridAlignmentRequest(request.commandId(), request.expectedVersion(),
                            request.imageRevision(), request.originX(), request.originY(), request.cellSize()));
            String imageViewId = requirePublicMapImages().latestReference(id, owner).orElse("");
            return MapGridAlignmentResponse.from(alignment, imageViewId);
        } catch (com.dndmaster.combatmap.application.view.MapGridAlignmentConflictException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/detect-boundaries")
    public MapBoundaryDetectionResponse detectBoundaries(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) MapBoundaryDetectionRequest request) {
        requestGuard.internal(token);
        requireRequest(request, "map boundary detection request is required");
        MapId id = new MapId(mapId);
        MapOwnerId owner = new MapOwnerId(request.ownerId());
        var alignment = requireAlignmentService().find(id, owner);
        if (alignment.version() < 1) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "map grid alignment must be confirmed before boundary detection");
        }
        try {
            return MapBoundaryDetectionResponse.from(mapViewService.proposeBoundaries(id, owner, alignment));
        } catch (com.dndmaster.combatmap.application.view.MapGridAlignmentConflictException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "map grid alignment changed while detecting boundaries", exception);
        }
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/movement-previews")
    public MovementPreviewResponse previewMovement(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) MovementPreviewRequestBody request) {
        requestGuard.internal(token);
        requireRequest(request, "movement preview request is required");
        if (request.playerId() == null || request.tokenId() == null || request.destination() == null
                || request.appliedEdition() == null || request.appliedEdition().isBlank()
                || request.expectedVersion() == null || request.expectedVersion() < 0 || invalid(request.destination())
                || request.waypoints() != null && (request.waypoints().size() > MovementPreviewRequest.MAX_WAYPOINTS
                        || request.waypoints().stream().anyMatch(CombatMapController::invalid))) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_PREVIEW");
        }
        MovementPreview preview = movementService.preview(new MovementPreviewRequest(
                new MapId(mapId), new PlayerId(request.playerId()), new TokenId(request.tokenId()),
                new GridPosition(request.destination().x(), request.destination().y()),
                request.waypoints() == null ? List.of() : request.waypoints().stream().map(position -> new GridPosition(position.x(), position.y())).toList(),
                request.appliedEdition(), request.expectedVersion()));
        return MovementPreviewResponse.from(mapId, preview);
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/movement-operations")
    public MovementOperationResponseBody startMovement(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) MovementStartRequestBody request) {
        requestGuard.internal(token);
        requireMovementStart(request);
        requireIdempotencyKey(idempotencyKey, request.commandId());
        return MovementOperationResponseBody.from(movementService.start(new MovementStartRequest(new MapId(mapId),
                new PlayerId(request.playerId()), new TokenId(request.tokenId()), movementPath(request.positions(), request.distance()),
                request.appliedEdition(), request.commandId(), request.fingerprint(), request.previewFingerprint(),
                request.waypoints() == null ? List.of() : request.waypoints().stream().map(position -> new GridPosition(position.x(), position.y())).toList(),
                request.expectedVersion())));
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/movement-operations/{operationId}/resume")
    public MovementOperationResponseBody resumeMovement(@PathVariable UUID mapId, @PathVariable UUID operationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) MovementCheckResultBody checkResult) {
        requestGuard.internal(token);
        MovementOperationResponse response = checkResult == null
                ? movementService.resume(new MapId(mapId), operationId)
                : movementService.resume(new MapId(mapId), operationId,
                        new com.dndmaster.combatmap.application.movement.MovementCheckResult(
                                checkResult.operationId(),
                                checkResult.checkId(), Boolean.TRUE.equals(checkResult.success()),
                                new com.dndmaster.combatmap.application.movement.MovementCheckOwner(
                                        checkResult.actor(), new PlayerId(checkResult.ownerPlayerId()))));
        return MovementOperationResponseBody.from(response);
    }

    @GetMapping("/internal/v1/combat-maps/{mapId}/movement-operations/{operationId}")
    public MovementOperationResponseBody movementOperation(@PathVariable UUID mapId, @PathVariable UUID operationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        return MovementOperationResponseBody.from(movementService.query(new MapId(mapId), operationId));
    }

    @GetMapping("/internal/v1/combat-maps/{mapId}/movement-operations")
    public org.springframework.http.ResponseEntity<MovementOperationResponseBody> latestMovementOperation(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        return movementService.latest(new MapId(mapId))
                .map(MovementOperationResponseBody::from)
                .map(org.springframework.http.ResponseEntity::ok)
                .orElseGet(() -> org.springframework.http.ResponseEntity.noContent().build());
    }

    @DeleteMapping("/internal/v1/combat-maps/{mapId}/movement-operations/{operationId}")
    public MovementOperationResponseBody cancelMovement(@PathVariable UUID mapId, @PathVariable UUID operationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requestGuard.internal(token);
        return MovementOperationResponseBody.from(movementService.cancel(new MapId(mapId), operationId));
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/spatial/observe")
    public SpatialRuntimeResponse observeSpatial(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) SpatialActionRequest request) {
        requestGuard.internal(token);
        requireSpatialAction(request);
        requireIdempotencyKey(idempotencyKey, request.commandId());
        return SpatialRuntimeResponse.from(mapId, movementService.observe(new MapId(mapId), new PlayerId(request.ownerId()),
                new TokenId(request.tokenId()), new GridPosition(request.x(), request.y()), request.expectedVersion(), request.commandId()));
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/spatial/interact")
    public SpatialRuntimeResponse interactSpatial(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) SpatialActionRequest request) {
        requestGuard.internal(token);
        requireSpatialAction(request);
        requireIdempotencyKey(idempotencyKey, request.commandId());
        return SpatialRuntimeResponse.from(requireSpatialRuntime().interact(new MapId(mapId), new MapOwnerId(request.ownerId()),
                new TokenId(request.tokenId()), new GridPosition(request.x(), request.y()), request.expectedVersion(), request.commandId()));
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/spatial/combat-turn-start")
    public SpatialRuntimeResponse combatTurnStartSpatial(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) SpatialTurnRequest request) {
        requestGuard.internal(token);
        requireSpatialTurn(request);
        requireIdempotencyKey(idempotencyKey, request.commandId());
        return SpatialRuntimeResponse.from(requireSpatialRuntime().combatTurnStart(new MapId(mapId), new MapOwnerId(request.ownerId()),
                request.expectedVersion(), request.commandId()));
    }

    @PostMapping("/internal/v1/combat-maps/{mapId}/spatial/advance-durations")
    public SpatialRuntimeResponse advanceSpatialDurations(@PathVariable UUID mapId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) SpatialTurnRequest request) {
        requestGuard.internal(token);
        requireSpatialTurn(request);
        requireIdempotencyKey(idempotencyKey, request.commandId());
        return SpatialRuntimeResponse.from(requireSpatialRuntime().advanceDurations(new MapId(mapId), new MapOwnerId(request.ownerId()),
                request.expectedVersion(), request.commandId()));
    }

    private static MovementPath movementPath(List<PositionRequest> positions, int distance) {
        return new MovementPath(positions.stream().map(position -> new GridPosition(position.x(), position.y())).toList(), distance);
    }

    private static void requireMovementStart(MovementStartRequestBody request) {
        if (request == null || request.playerId() == null || request.tokenId() == null || request.commandId() == null
                || request.appliedEdition() == null || request.appliedEdition().isBlank() || request.fingerprint() == null || request.fingerprint().isBlank()
                || request.expectedVersion() == null || request.expectedVersion() < 0 || request.distance() == null || request.distance() < 1
                || request.positions() == null || request.positions().size() < 2 || request.positions().stream().anyMatch(CombatMapController::invalid)
                || request.waypoints() != null && (request.waypoints().size() > MovementPreviewRequest.MAX_WAYPOINTS
                        || request.waypoints().stream().anyMatch(CombatMapController::invalid))) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_OPERATION");
        }
        if (request.previewFingerprint() == null || request.previewFingerprint().isBlank()) {
            throw new ApiRequestGuard.ApiContractException(400, "MOVEMENT_PREVIEW_REQUIRED");
        }
    }

    private static void requireSpatialAction(SpatialActionRequest request) {
        if (request == null || request.ownerId() == null || request.tokenId() == null || request.commandId() == null
                || request.expectedVersion() == null || request.expectedVersion() < 0 || request.x() == null || request.y() == null
                || request.x() < 0 || request.y() < 0) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SPATIAL_ACTION");
        }
    }

    private static void requireSpatialTurn(SpatialTurnRequest request) {
        if (request == null || request.ownerId() == null || request.commandId() == null
                || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SPATIAL_ACTION");
        }
    }

    private com.dndmaster.combatmap.application.spatial.SpatialFeatureRuntimeApplicationService requireSpatialRuntime() {
        if (spatialRuntime == null) throw new IllegalStateException("spatial runtime is unavailable");
        return spatialRuntime;
    }

    private static boolean invalid(PositionRequest position) {
        return position == null || position.x() == null || position.y() == null || position.x() < 0 || position.y() < 0;
    }

    public CombatMapMoveResponse movePlayer(UUID mapId, String token, MoveRequest request) {
        return movePlayerInternal(mapId, token,
                request == null || request.commandId() == null ? null : request.commandId().toString(), request);
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
        if (request.commandId() == null) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
        requireIdempotencyKey(idempotencyKey, request.commandId());
        if (request.playerId() == null || request.tokenId() == null || request.appliedEdition() == null
                || request.appliedEdition().isBlank() || request.commandId() == null || request.distance() == null
                || request.distance() < 1 || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.fingerprint() != null && request.fingerprint().isBlank()
                || request.positions() == null || request.positions().size() < 2
                || request.positions().stream().anyMatch(CombatMapController::invalid)
                || request.waypoints() != null && (request.waypoints().size() > MovementPreviewRequest.MAX_WAYPOINTS
                        || request.waypoints().stream().anyMatch(CombatMapController::invalid))) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
        if (request.previewFingerprint() == null || request.previewFingerprint().isBlank()) {
            throw new ApiRequestGuard.ApiContractException(400, "MOVEMENT_PREVIEW_REQUIRED");
        }
        MovementPath path = new MovementPath(
                request.positions().stream().map(p -> new GridPosition(p.x(), p.y())).toList(),
                request.distance());
        List<GridPosition> waypoints = request.waypoints() == null ? List.of()
                : request.waypoints().stream().map(p -> new GridPosition(p.x(), p.y())).toList();
        String previewFingerprint = request.previewFingerprint();
        MovementOperationResponse operation = movementService.start(new MovementStartRequest(new MapId(mapId),
                new PlayerId(request.playerId()), new TokenId(request.tokenId()), path, request.appliedEdition(),
                request.commandId(), request.fingerprint() == null ? "legacy:" + request.commandId() : request.fingerprint(),
                previewFingerprint, waypoints, request.expectedVersion()));
        return CombatMapMoveResponse.from(mapId, operation);
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

    @PutMapping("/internal/v1/combat-maps/{mapId}/layout")
    public CombatMapAiStateResponse updateLayout(@PathVariable UUID mapId, @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) LayoutRequest request) {
        requestGuard.internal(token); requireRequest(request, "layout request is required");
        try {
            Set<GridPosition> obstacles = authoredPositions(request.obstacles(), "obstacles");
            List<Door> doors = authoredPositions(request.doors(), "doors").stream().map(position -> new Door(position, false)).toList();
            List<MapBoundary> boundaries = request.boundaries() == null ? List.of() : request.boundaries().stream().map(MapBoundary::parse).toList();
            GridPosition playerStart = request.playerStart() == null ? null : new GridPosition(request.playerStart().x(), request.playerStart().y());
            CombatMap map = mapViewService.updateLayout(new MapId(mapId), new MapOwnerId(request.ownerId()), request.expectedVersion(), request.commandId(), obstacles, doors, boundaries, request.crop(), request.alignmentVersion(), request.imageRevision(), playerStart);
            return new CombatMapAiStateResponse(map.id().value());
        } catch (IllegalStateException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
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
            List<PositionRequest> positions, Integer distance,
            String appliedEdition, UUID commandId, Long expectedVersion,
            String fingerprint, String previewFingerprint, List<PositionRequest> waypoints) {
        public MoveRequest(UUID playerId, UUID tokenId, List<PositionRequest> positions, Integer distance,
                String appliedEdition, UUID commandId, Long expectedVersion, String fingerprint,
                List<PositionRequest> waypoints) {
            this(playerId, tokenId, positions, distance, appliedEdition, commandId, expectedVersion,
                    fingerprint, null, waypoints);
        }
    }

    public record MovementPreviewRequestBody(UUID playerId, UUID tokenId, PositionRequest destination,
            List<PositionRequest> waypoints, String appliedEdition, Long expectedVersion) {}

    public record PositionRequest(Integer x, Integer y) {}

    public record SpatialActionRequest(UUID ownerId, UUID tokenId, Integer x, Integer y, Long expectedVersion, UUID commandId) {}
    public record SpatialTurnRequest(UUID ownerId, Long expectedVersion, UUID commandId) {}
    public record SpatialRuntimeResponse(UUID mapId, long mapVersion, List<String> publicEvents, UUID operationId,
            String status, com.dndmaster.combatmap.application.movement.PendingMovementCheck pendingCheck) {
        public SpatialRuntimeResponse(UUID mapId, long mapVersion, List<String> publicEvents) {
            this(mapId, mapVersion, publicEvents, null, null, null);
        }
        static SpatialRuntimeResponse from(com.dndmaster.combatmap.application.spatial.SpatialRuntimeResult result) {
            return new SpatialRuntimeResponse(result.mapId().value(), result.mapVersion(), result.publicEvents());
        }
        static SpatialRuntimeResponse from(UUID mapId,
                com.dndmaster.combatmap.application.movement.MovementOperationResponse result) {
            long version = result.result() == null ? 0 : result.result().mapVersion();
            List<String> events = result.result() == null ? List.of() : result.result().publicEvents();
            return new SpatialRuntimeResponse(mapId, version, events, result.operationId(), result.status().name(),
                    result.pendingCheck());
        }
    }

    public record AiStateRequest(
            UUID ownerId, UUID tokenId,
            int x, int y,
            UUID commandId,
            long expectedVersion,
            List<LayerRequest> layers) {}
    public record DoorRequest(UUID ownerId,int x,int y,boolean open,UUID commandId,long expectedVersion) {}
    public record LayoutRequest(UUID ownerId, long expectedVersion, UUID commandId, List<String> obstacles, List<String> doors,
                                List<String> boundaries, String crop, Long alignmentVersion, String imageRevision, PositionRequest playerStart) {
        public LayoutRequest(UUID ownerId, long expectedVersion, UUID commandId, List<String> obstacles,
                List<String> doors, List<String> boundaries, String crop) {
            this(ownerId, expectedVersion, commandId, obstacles, doors, boundaries, crop, null, "", null);
        }
    }
    public record GridCalibrationRequest(UUID ownerId, long expectedVersion, int width, int height, int cellSize,
                                         int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {}
    public record MapGridAlignmentRequest(UUID ownerId, UUID commandId, long expectedVersion, String imageRevision,
                                          double originX, double originY, double cellSize) {}
    public record MapBoundaryDetectionRequest(UUID ownerId) {}
    public record MapBoundaryDetectionResponse(long mapVersion, List<String> obstacles, List<String> doors,
            List<String> boundaries, String crop, List<com.dndmaster.combatmap.application.view.MapBoundaryCandidate> candidates,
            long alignmentVersion, String imageRevision) {
        public MapBoundaryDetectionResponse(long mapVersion, List<String> obstacles, List<String> doors,
                List<String> boundaries, String crop) {
            this(mapVersion, obstacles, doors, boundaries, crop, List.of(), 0, "");
        }
        public MapBoundaryDetectionResponse(long mapVersion, List<String> obstacles, List<String> doors,
                List<String> boundaries, String crop, List<com.dndmaster.combatmap.application.view.MapBoundaryCandidate> candidates) {
            this(mapVersion, obstacles, doors, boundaries, crop, candidates, 0, "");
        }
        static MapBoundaryDetectionResponse from(com.dndmaster.combatmap.application.view.CombatMapViewService.BoundaryDraft draft) {
            return new MapBoundaryDetectionResponse(draft.mapVersion(),
                    draft.obstacles().stream().map(position -> position.x() + "," + position.y()).sorted().toList(),
                    draft.doors().stream().map(door -> door.position().x() + "," + door.position().y()).sorted().toList(),
                    draft.boundaries().stream().map(MapBoundary::encoded).sorted().toList(), draft.crop(), draft.candidates(),
                    draft.alignmentVersion(), draft.imageRevision());
        }
    }
    public record MapGridAlignmentResponse(UUID mapId, long version, String imageRevision, String imageViewId, double originX, double originY, double cellSize) {
        static MapGridAlignmentResponse from(com.dndmaster.combatmap.application.view.MapGridAlignment alignment, String imageViewId) {
            return new MapGridAlignmentResponse(alignment.mapId().value(), alignment.version(), alignment.imageRevision(), imageViewId, alignment.originX(), alignment.originY(), alignment.cellSize());
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
    private com.dndmaster.combatmap.application.view.PublicMapImageArtifactService requirePublicMapImages() {
        if (publicMapImages == null) throw new IllegalStateException("public map image unavailable");
        return publicMapImages;
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

    public record CombatMapMoveResponse(UUID mapId, long version, UUID operationId, String status, String outcomeStatus,
            List<PositionRequest> requestedPath, List<PositionRequest> traversedPath, PositionRequest finalPosition,
            List<String> publicEvents, String interruptionReason) {
        public CombatMapMoveResponse(UUID mapId, long version) {
            this(mapId, version, null, "COMMITTED", "COMMITTED", List.of(), List.of(), null, List.of(), null);
        }
        public CombatMapMoveResponse(UUID mapId) { this(mapId, 0); }
        static CombatMapMoveResponse from(UUID mapId, MovementOperationResponse response) {
            var result = response.result();
            return new CombatMapMoveResponse(mapId, result == null ? 0 : result.mapVersion(), response.operationId(),
                    response.status().name(), response.outcomeStatus().name(),
                    result == null ? List.of() : result.requestedPath().orderedPositions().stream()
                            .map(position -> new PositionRequest(position.x(), position.y())).toList(),
                    result == null ? List.of() : result.traversedPath().stream()
                            .map(position -> new PositionRequest(position.x(), position.y())).toList(),
                    result == null ? null : new PositionRequest(result.finalPosition().x(), result.finalPosition().y()),
                    result == null ? List.of() : result.publicEvents(), result == null ? null : result.interruptionReason());
        }
    }

    public record MovementStartRequestBody(UUID playerId, UUID tokenId, List<PositionRequest> positions, Integer distance,
            String appliedEdition, UUID commandId, Long expectedVersion, String fingerprint, String previewFingerprint,
            List<PositionRequest> waypoints) {
        public MovementStartRequestBody(UUID playerId, UUID tokenId, List<PositionRequest> positions, Integer distance,
                String appliedEdition, UUID commandId, Long expectedVersion, String fingerprint) {
            this(playerId, tokenId, positions, distance, appliedEdition, commandId, expectedVersion, fingerprint, null, List.of());
        }
    }

    public record MovementOperationResponseBody(UUID operationId, String status, String outcomeStatus,
            List<PositionRequest> requestedPath, List<PositionRequest> traversedPath, PositionRequest finalPosition, Long mapVersion,
            List<String> publicEvents, String interruptionReason, PendingCheckResponse pendingCheck,
            PendingCheckDetailsResponse pendingCheckDetails) {
        public record PendingCheckResponse(UUID checkId, UUID operationId, String label, String diceExpression,
                UUID ownerPlayerId, com.dndmaster.combatmap.application.movement.MovementCheckActor actor) {}
        public record PendingCheckDetailsResponse(UUID checkId, UUID operationId, String ruleReference,
                Integer difficulty, UUID ownerPlayerId,
                com.dndmaster.combatmap.application.movement.MovementCheckActor actor) {}
        static MovementOperationResponseBody from(MovementOperationResponse response) {
            var result = response.result();
            return new MovementOperationResponseBody(response.operationId(), response.status().name(), response.outcomeStatus().name(),
                    result == null ? List.of() : result.requestedPath().orderedPositions().stream().map(position -> new PositionRequest(position.x(), position.y())).toList(),
                    result == null ? List.of() : result.traversedPath().stream().map(position -> new PositionRequest(position.x(), position.y())).toList(),
                    result == null ? null : new PositionRequest(result.finalPosition().x(), result.finalPosition().y()),
                    result == null ? null : result.mapVersion(), result == null ? List.of() : result.publicEvents(),
                    result == null ? null : result.interruptionReason(), response.pendingCheck() == null ? null
                            : new PendingCheckResponse(response.pendingCheck().checkId(), response.pendingCheck().operationId(),
                                    response.pendingCheck().label(), response.pendingCheck().diceExpression(),
                                    response.pendingCheck().owner().playerId().value(), response.pendingCheck().owner().actor()),
                    response.pendingCheckDetails() == null ? null
                            : new PendingCheckDetailsResponse(response.pendingCheckDetails().checkId(), response.pendingCheckDetails().operationId(),
                                    response.pendingCheckDetails().ruleReference(), response.pendingCheckDetails().difficulty(),
                                    response.pendingCheckDetails().owner().playerId().value(), response.pendingCheckDetails().owner().actor()));
        }
    }

    public record MovementPreviewResponse(UUID mapId, List<PositionRequest> orderedPositions,
            int distance, long baseMapVersion, String fingerprint) {
        static MovementPreviewResponse from(UUID mapId, MovementPreview preview) {
            return new MovementPreviewResponse(mapId, preview.orderedPositions().stream()
                    .map(position -> new PositionRequest(position.x(), position.y())).toList(),
                    preview.distance(), preview.baseMapVersion(), preview.fingerprint());
        }
    }

    public record CombatMapAiStateResponse(UUID mapId) {}

    public record PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId,
                                 UUID mapDefinitionId, String assetId, String assetLocator,
                                 Integer playerSpawnX, Integer playerSpawnY, String sourceImage, String sourceImageContentType,
                                 TacticalSceneMaterialization tacticalScene, Integer stagePosition,
                                 UUID playerTokenId, UUID situationId, Long situationRevision, Integer turnIndex,
                                 String currentScene, String location, String entryEvidence,
                                 List<String> walls, List<String> doors, List<String> obstacles,
                                 UUID sourceDocumentId, String sourceAssetLocator,
                                 String spatialPreparationReference,
                                 List<SpatialFeaturePlacementRequest> spatialPlacements,
                                 boolean spatialPreparationBlocked, List<String> spatialWarnings, List<String> spatialFailures,
                                 UUID commandId, long expectedVersion, String operationFingerprint) {
        public PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId,
                UUID mapDefinitionId, String assetId, String assetLocator,
                Integer playerSpawnX, Integer playerSpawnY, String sourceImage, String sourceImageContentType,
                TacticalSceneMaterialization tacticalScene, Integer stagePosition, UUID playerTokenId,
                UUID situationId, Long situationRevision, Integer turnIndex, String currentScene, String location,
                String entryEvidence, List<String> walls, List<String> doors, List<String> obstacles,
                UUID sourceDocumentId, String sourceAssetLocator) {
            this(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, playerSpawnX, playerSpawnY,
                    sourceImage, sourceImageContentType, tacticalScene, stagePosition, playerTokenId, situationId,
                    situationRevision, turnIndex, currentScene, location, entryEvidence, walls, doors, obstacles,
                    sourceDocumentId, sourceAssetLocator, "story-plan:unknown", List.of(), false, List.of(), List.of(), UUID.randomUUID(), 0,
                    "legacy-preparation-command");
        }

        public PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId, String assetId,
                String assetLocator, Integer playerSpawnX, Integer playerSpawnY) {
            this(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, playerSpawnX, playerSpawnY,
                    null, null, null, null, null, UUID.randomUUID(), 1L, 0, "unknown", "unknown", "",
                    List.of(), List.of(), List.of(), null, null, "story-plan:unknown", List.of(), false, List.of(), List.of(), UUID.randomUUID(), 0,
                    "legacy-preparation-command");
        }
        public PrepareRequest(UUID adventureId, UUID ownerId, UUID ruleSetId, UUID mapDefinitionId, String assetId,
                String assetLocator, Integer playerSpawnX, Integer playerSpawnY, String sourceImage,
                String sourceImageContentType, TacticalSceneMaterialization tacticalScene, Integer stagePosition) {
            this(adventureId, ownerId, ruleSetId, mapDefinitionId, assetId, assetLocator, playerSpawnX, playerSpawnY,
                    sourceImage, sourceImageContentType, tacticalScene, stagePosition, null, UUID.randomUUID(), 1L, 0,
                    "unknown", "unknown", "", List.of(), List.of(), List.of(), null, null, "story-plan:unknown", List.of(), false, List.of(), List.of(), UUID.randomUUID(), 0,
                    "legacy-preparation-command");
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
            entryEvidence = entryEvidence == null ? "" : entryEvidence.trim();
            walls = walls == null ? List.of() : List.copyOf(walls);
            doors = doors == null ? List.of() : List.copyOf(doors);
            obstacles = obstacles == null ? List.of() : List.copyOf(obstacles);
            sourceAssetLocator = sourceAssetLocator == null ? "" : sourceAssetLocator.trim();
            spatialPreparationReference = spatialPreparationReference == null || spatialPreparationReference.isBlank()
                    ? "story-plan:unknown" : spatialPreparationReference.trim();
            spatialPlacements = spatialPlacements == null ? List.of() : List.copyOf(spatialPlacements);
            spatialWarnings = spatialWarnings == null ? List.of() : List.copyOf(spatialWarnings);
            spatialFailures = spatialFailures == null ? List.of() : List.copyOf(spatialFailures);
            if (commandId == null) throw new IllegalArgumentException("preparation command id must be present");
            if (expectedVersion < 0) throw new IllegalArgumentException("preparation expected version must not be negative");
            operationFingerprint = operationFingerprint == null || operationFingerprint.isBlank()
                    ? "legacy-preparation-command" : operationFingerprint.trim();
        }
    }

    private static SpatialFeaturePlacementBatch spatialBatch(PrepareRequest request) {
        List<SpatialFeaturePlacementBatch.Placement> placements = request.spatialPlacements().stream().map(item -> {
            SpatialFeatureType type;
            try { type = SpatialFeatureType.valueOf(item.type()); }
            catch (RuntimeException exception) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid spatial feature type", exception); }
            Set<SpatialTrigger> triggers = requestTriggers(item.triggers());
            DetectionSpec detection = item.detectionRuleReference() == null || item.detectionRuleReference().isBlank()
                    || item.detectionMode() == null || item.detectionMode().isBlank()
                    ? null : new DetectionSpec(item.detectionRuleReference(), item.detectionDifficulty(), item.detectionMode());
            var evidence = item.evidence();
            if (evidence == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "structured spatial evidence is required");
            return new SpatialFeaturePlacementBatch.Placement(item.featureId(), type, item.required(),
                    authoredPositions(item.cells(), "spatial feature").stream().toList(),
                    new SpatialFeaturePlacementBatch.Evidence(evidence.sourceDocumentId(), evidence.sourceExtractionVersion(),
                            evidence.sourceLocator(), evidence.resolutionUnitId(), evidence.scenarioPackageVersion(),
                            authoredPositions(evidence.allowedCells(), "allowed spatial feature")), detection, triggers,
                    item.durationTurns(), item.removalPolicy(), item.overlapAllowed(), item.repeatable());
        }).toList();
        return new SpatialFeaturePlacementBatch(request.spatialPreparationReference(), request.spatialPreparationBlocked(),
                placements, request.spatialWarnings(), request.spatialFailures());
    }

    private static Set<SpatialTrigger> requestTriggers(List<String> values) {
        return (values == null ? List.<String>of() : values).stream().map(value -> {
            try { return SpatialTrigger.valueOf(value); }
            catch (RuntimeException exception) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid spatial trigger", exception); }
        }).collect(java.util.stream.Collectors.toSet());
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
    public record SpatialFeaturePlacementRequest(UUID featureId, String type, boolean required, List<String> cells,
            SpatialEvidenceRequest evidence, String detectionRuleReference, Integer detectionDifficulty,
            String detectionMode, List<String> triggers, int durationTurns, String removalPolicy,
            boolean overlapAllowed, boolean repeatable) {
        public SpatialFeaturePlacementRequest {
            cells = cells == null ? List.of() : List.copyOf(cells);
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
            removalPolicy = removalPolicy == null ? "" : removalPolicy;
        }
        public SpatialFeaturePlacementRequest(UUID featureId, String type, boolean required, List<String> cells,
                SpatialEvidenceRequest evidence, String detectionRuleReference, Integer detectionDifficulty,
                String detectionMode, List<String> triggers) {
            this(featureId, type, required, cells, evidence, detectionRuleReference, detectionDifficulty,
                    detectionMode, triggers, -1, "", false, false);
        }
    }

    public record SpatialEvidenceRequest(UUID sourceDocumentId, long sourceExtractionVersion, String sourceLocator,
            String resolutionUnitId, String scenarioPackageVersion, List<String> allowedCells) {}

    public enum PrepareStatus { READY, BLOCKED }

    public record PrepareResponse(UUID mapId, PrepareStatus status, int warningCount) {
        public PrepareResponse(UUID mapId) { this(mapId, PrepareStatus.READY, 0); }
    }

    public record ReplayRequest(UUID adventureId, UUID ownerId, UUID commandId, String commandFingerprint) {}
}
