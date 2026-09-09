package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public final class CombatMapViewService {
    private final CombatMapViewStore store;
    private final MapFilePreparationPort filePort;
    private final AiMapGenerationPort aiPort;
    private final PublicMapImageArtifactService publicImages;
    private final MapGridAlignmentStore alignments;

    public CombatMapViewService(CombatMapViewStore store, MapFilePreparationPort filePort, AiMapGenerationPort aiPort) {
        this(store, filePort, aiPort, null);
    }
    public CombatMapViewService(CombatMapViewStore store, MapFilePreparationPort filePort, AiMapGenerationPort aiPort,
            PublicMapImageArtifactService publicImages) {
        this(store, filePort, aiPort, publicImages, null);
    }
    public CombatMapViewService(CombatMapViewStore store, MapFilePreparationPort filePort, AiMapGenerationPort aiPort,
            PublicMapImageArtifactService publicImages, MapGridAlignmentStore alignments) {
        this.store = Objects.requireNonNull(store); this.filePort = Objects.requireNonNull(filePort); this.aiPort = Objects.requireNonNull(aiPort); this.publicImages = publicImages; this.alignments = alignments;
    }
    public CombatMap prepareUploaded(MapOwnerId owner, AdventureId adventure, RuleSetId rules, UploadedMapSource source) { return saveNew(owner, adventure, rules, filePort.prepare(source)); }
    public CombatMap prepareGenerated(MapOwnerId owner, AdventureId adventure, RuleSetId rules, String description) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        return saveNew(owner, adventure, rules, aiPort.generate(description.trim()));
    }
    public CombatMap prepareGenerated(MapOwnerId owner, AdventureId adventure, RuleSetId rules, String description,
            Collection<GridPosition> obstacles, Collection<Door> doors) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        return prepareGenerated(owner, adventure, rules,
                new MapGenerationRequest(description.trim(), "", 20, 20, 30, 5,
                        obstacles == null ? Set.of() : obstacles,
                        doors == null ? List.of() : doors));
    }
    public CombatMap prepareGenerated(MapOwnerId owner, AdventureId adventure, RuleSetId rules,
            MapGenerationRequest request) {
        return prepareGenerated(owner, adventure, rules, request, true);
    }
    public CombatMap prepareGenerated(MapOwnerId owner, AdventureId adventure, RuleSetId rules,
            MapGenerationRequest request, boolean includeAiPlayerStart) {
        PreparedMapData generated = aiPort.generate(request);
        Set<GridPosition> mergedObstacles = new HashSet<>(generated.obstacles());
        mergedObstacles.addAll(request.authoredObstacles());
        List<Door> mergedDoors = new ArrayList<>(generated.doors());
        mergedDoors.addAll(request.authoredDoors());
        return saveNew(owner, adventure, rules, new PreparedMapData(generated.grid(), generated.tokens(), mergedObstacles, generated.layers(), mergedDoors), null, null, includeAiPlayerStart);
    }
    public CombatMap prepareGenerated(MapOwnerId owner, AdventureId adventure, RuleSetId rules, String description, int spawnX, int spawnY) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        return saveNew(owner, adventure, rules, aiPort.generate(description.trim()), spawnX, spawnY);
    }
    public CombatMap prepareTactical(MapOwnerId owner, AdventureId adventure, RuleSetId rules, String description,
            TacticalSceneMaterialization scene) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        PreparedMapData source = aiPort.generate(description.trim());
        PreparedMapData tactical = scene.materialize(source.grid(), owner.value());
        return saveNew(owner, adventure, rules, tactical);
    }
    public CombatMap prepareTactical(MapOwnerId owner, AdventureId adventure, RuleSetId rules, String description,
            UploadedMapSource source, TacticalSceneMaterialization scene) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        PreparedMapData prepared = filePort.prepare(source);
        PreparedMapData tactical = scene.materialize(prepared.grid(), owner.value());
        return saveNew(owner, adventure, rules, new PreparedMapData(prepared.grid(), tactical.tokens(), tactical.obstacles(),
                java.util.stream.Stream.concat(prepared.layers().stream(), tactical.layers().stream()).toList()));
    }
    private CombatMap saveNew(MapOwnerId owner, AdventureId adventure, RuleSetId rules, PreparedMapData data) { return saveNew(owner, adventure, rules, data, null, null, true); }
    private CombatMap saveNew(MapOwnerId owner, AdventureId adventure, RuleSetId rules, PreparedMapData data, Integer spawnX, Integer spawnY) { return saveNew(owner, adventure, rules, data, spawnX, spawnY, true); }
    private CombatMap saveNew(MapOwnerId owner, AdventureId adventure, RuleSetId rules, PreparedMapData data, Integer spawnX, Integer spawnY, boolean includeAiPlayerStart) {
        List<CombatToken> tokens = new ArrayList<>(data.tokens());
        if (!includeAiPlayerStart) {
            // 준비/활성화 경계에서는 AI가 제안한 PLAYER를 위치 결정으로
            // 오인하지 않도록 제거하고, 활성화 때 상황 문맥으로 다시 만든다.
            tokens.removeIf(token -> token.type() == TokenType.PLAYER);
        }
        if (includeAiPlayerStart) {
            data.layers().stream().filter(layer -> layer.type().equals("GM_PLAYER_START")).findFirst()
                    .flatMap(layer -> parsePosition(layer.value())).ifPresent(position -> {
                        if (tokens.stream().noneMatch(token -> token.type() == TokenType.PLAYER)) {
                            tokens.add(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                                    position, TokenController.PLAYER, new PlayerId(owner.value())));
                        }
                    });
        }
        if (tokens.stream().noneMatch(token -> token.type() == TokenType.PLAYER) && spawnX != null && spawnY != null) {
            tokens.add(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                    new GridPosition(spawnX, spawnY), TokenController.PLAYER, new PlayerId(owner.value())));
        }
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), adventure, rules, data.grid(), new PlayerId(owner.value()), tokens, data.obstacles(), data.layers(), 0, null);
        map.replaceDoors(data.doors());
        map.refreshVisibility(0);
        store.insert(owner, map); return map;
    }

    private static Optional<GridPosition> parsePosition(String value) {
        if (value == null) return Optional.empty();
        String[] pair = value.trim().split(",", -1);
        if (pair.length != 2) return Optional.empty();
        try { return Optional.of(new GridPosition(Integer.parseInt(pair[0].trim()), Integer.parseInt(pair[1].trim()))); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }
    public CombatMap controlAiState(MapId id, MapOwnerId owner, long expectedVersion, UUID commandId, TokenId tokenId, GridPosition position, List<MapLayer> aiLayers) {
        VersionedOwnedCombatMap replay = store.findByCommandId(commandId).orElse(null);
        if (replay != null) {
            String fingerprint = commandId + "|" + owner + "|" + tokenId + "|" + position + "|" + aiLayers;
            if (!fingerprint.equals(replay.map().operationFingerprint())) throw new IllegalStateException("combat map command id reused with different payload");
            return replay.map();
        }
        VersionedOwnedCombatMap state = owned(id, owner);
        if (state.version() != expectedVersion) throw new IllegalStateException("version mismatch");
        if (aiLayers.stream().anyMatch(l -> l.visibility() != LayerVisibility.AI_ONLY)) throw new IllegalArgumentException("AI control accepts only AI_ONLY layers");
        List<MapLayer> layers = new ArrayList<>(state.map().layers().stream().filter(l -> l.visibility() == LayerVisibility.PLAYER_VISIBLE).toList()); layers.addAll(aiLayers);
        String fingerprint = commandId + "|" + owner + "|" + tokenId + "|" + position + "|" + aiLayers;
        List<CombatToken> tokens = state.map().tokens().stream().map(t -> {
            if (!t.id().equals(tokenId)) return copy(t, t.position());
            if (t.controller() != TokenController.AI_GAME_MASTER) throw new CombatMapAccessDeniedException();
            return copy(t, position);
        }).toList();
        if (tokens.stream().noneMatch(t -> t.id().equals(tokenId))) throw new CombatMapAccessDeniedException();
        CombatMap updated = new CombatMap(state.map().id(), state.map().adventureId(), state.map().ruleSetId(), state.map().grid(), state.map().ownerPlayerId(), tokens, state.map().obstacles(), layers, expectedVersion + 1, commandId, fingerprint);
        updated.replaceRuntimeState(state.map().runtimeState());
        VisibilitySnapshot prior = state.map().visibilitySnapshot();
        updated.replaceDoors(state.map().doors());
        updated.refreshVisibility(prior == null ? 0 : prior.ruleTurn() + 1);
        store.update(owner, updated, expectedVersion); return updated;
    }
    public PlayerCombatMapView displayForPlayer(MapId id, MapOwnerId owner) { VersionedOwnedCombatMap state = owned(id, owner); observePublicImage(id, owner, state); return projection(state.map(), state.version()); }
    public CombatMap displayForGm(MapId id, MapOwnerId owner) { return owned(id, owner).map(); }
    public Optional<PlayerCombatMapView> displayForAdventure(AdventureId adventureId, MapOwnerId owner) { return store.findByAdventureId(adventureId, owner).map(state -> { observePublicImage(state.map().id(), owner, state); return projection(state.map(), state.version()); }); }
    public Optional<PlayerCombatMapView> displayForPreparation(AdventureId adventureId, MapOwnerId owner) {
        return store.findPreparedByAdventureId(adventureId, owner).or(() -> store.findByAdventureId(adventureId, owner))
                .map(state -> new PlayerCombatMapView(state.map().id(), state.map().grid(), state.map().tokens(),
                state.map().obstacles(), state.map().doors().stream().toList(), playerSafeLayers(state.map()), Set.of(), Set.of(), Set.of(), state.version()));
    }
    public Optional<MapId> preparedMapIdForAdventure(AdventureId adventureId, MapOwnerId owner) {
        return store.findPreparedByAdventureId(adventureId, owner).map(state -> state.map().id());
    }
    public void activateForAdventure(MapId id, MapOwnerId owner, int stagePosition) {
        activateForAdventure(id, owner, MapActivationContext.atStage(stagePosition));
    }
    public CombatMap activateForAdventure(MapId id, MapOwnerId owner, MapActivationContext context) {
        VersionedOwnedCombatMap state = owned(id, owner);
        List<CombatToken> nonPlayers = state.map().tokens().stream().filter(t -> t.type() != TokenType.PLAYER).toList();
        Set<GridPosition> occupied = nonPlayers.stream().map(CombatToken::position).collect(Collectors.toSet());
        Optional<GridPosition> tactical = state.map().tokens().stream().filter(t -> t.type() == TokenType.PLAYER).map(CombatToken::position).findFirst();
        SpawnResolution resolution = new SpawnResolutionPolicy().resolve(state.map().grid(), state.map().obstacles(), state.map().doors(), occupied, context, tactical);
        List<CombatToken> tokens = new ArrayList<>(nonPlayers);
        tokens.add(new CombatToken(state.map().tokens().stream().filter(t -> t.type() == TokenType.PLAYER).findFirst().map(CombatToken::id)
                .orElse(context.playerTokenId().map(TokenId::new).orElse(new TokenId(UUID.randomUUID()))),
                TokenType.PLAYER, resolution.position(), TokenController.PLAYER, new PlayerId(owner.value())));
        CombatMap activated = new CombatMap(state.map().id(), state.map().adventureId(), state.map().ruleSetId(), state.map().grid(), state.map().ownerPlayerId(), tokens, state.map().obstacles(), state.map().layers(), state.version() + 1, UUID.randomUUID(), "ACTIVATE|" + context + "|" + resolution);
        activated.replaceDoors(state.map().doors());
        activated.replaceRuntimeState(state.map().runtimeState());
        activated.refreshVisibility(0);
        store.activate(owner, activated, state.version(), context.stagePosition(), activated.operationKey(), activated.operationFingerprint());
        return activated;
    }
    public CombatMap revealToken(MapId id, MapOwnerId owner, long expectedVersion, UUID commandId, TokenId tokenId) {
        VersionedOwnedCombatMap state=owned(id, owner);
        String fingerprint=id+"|"+owner+"|REVEAL|"+tokenId; CombatMap replay=replay(id,owner,commandId,fingerprint); if(replay!=null)return replay;
        if(state.version()!=expectedVersion) throw new IllegalStateException("version mismatch");
        CombatMap map=state.map(); map.tokens().stream().filter(token->token.id().equals(tokenId)).findFirst().orElseThrow(CombatMapAccessDeniedException::new).reveal();
        map.refreshVisibility(map.visibilitySnapshot()==null?0:map.visibilitySnapshot().ruleTurn());
        store.update(owner,map,expectedVersion,expectedVersion+1,commandId,fingerprint); return map;
    }
    public CombatMap changeDoor(MapId id, MapOwnerId owner, long expectedVersion, UUID commandId, GridPosition position, boolean open) {
        VersionedOwnedCombatMap state=owned(id, owner);
        String fingerprint=id+"|"+owner+"|DOOR|"+position+"|"+open; CombatMap replay=replay(id,owner,commandId,fingerprint); if(replay!=null)return replay;
        if(state.version()!=expectedVersion) throw new IllegalStateException("version mismatch");
        Set<Door> doors=new HashSet<>(state.map().doors()); doors.removeIf(door->door.position().equals(position)); doors.add(new Door(position,open)); state.map().replaceDoors(doors); state.map().refreshVisibility(state.map().visibilitySnapshot()==null?0:state.map().visibilitySnapshot().ruleTurn());
        store.update(owner,state.map(),expectedVersion,expectedVersion+1,commandId,fingerprint); return state.map();
    }

    /** 맵 시작 전 사용자가 AI 초안을 검수해 벽·문·자르기 영역을 확정한다. */
    public CombatMap updateLayout(MapId id, MapOwnerId owner, long expectedVersion, UUID commandId,
            Set<GridPosition> obstacles, Collection<Door> doors, String crop) {
        return updateLayout(id, owner, expectedVersion, commandId, obstacles, doors, List.of(), crop, null, "");
    }

    public CombatMap updateLayout(MapId id, MapOwnerId owner, long expectedVersion, UUID commandId,
            Set<GridPosition> obstacles, Collection<Door> doors, Collection<MapBoundary> boundaries, String crop) {
        return updateLayout(id, owner, expectedVersion, commandId, obstacles, doors, boundaries, crop, null, "");
    }

    /** 정렬 버전이 감지·검수 시작 시점과 같은지 확인한 뒤 맵 초안을 저장한다. */
    public CombatMap updateLayout(MapId id, MapOwnerId owner, long expectedVersion,
            UUID commandId, Set<GridPosition> obstacles, Collection<Door> doors,
            Collection<MapBoundary> boundaries, String crop, Long alignmentVersion, String imageRevision) {
        VersionedOwnedCombatMap state = owned(id, owner);
        String fingerprint = id + "|" + owner + "|LAYOUT|" + obstacles + "|" + doors + "|" + boundaries + "|" + crop
                + "|ALIGNMENT=" + alignmentVersion + "|IMAGE=" + imageRevision;
        CombatMap replay = replay(id, owner, commandId, fingerprint);
        if (replay != null) return replay;
        if (state.version() != expectedVersion) throw new IllegalStateException("version mismatch");
        if (alignmentVersion != null || (imageRevision != null && !imageRevision.isBlank())) {
            if (alignments == null) throw new MapGridAlignmentConflictException();
            MapGridAlignment current = alignments.find(id).orElseThrow(MapGridAlignmentConflictException::new);
            if (alignmentVersion == null || imageRevision == null || imageRevision.isBlank()
                    || current.version() != alignmentVersion || !current.imageRevision().equals(imageRevision)) {
                throw new MapGridAlignmentConflictException();
            }
        }
        Set<GridPosition> nextObstacles = Set.copyOf(obstacles == null ? Set.of() : obstacles);
        List<Door> nextDoors = List.copyOf(doors == null ? List.of() : doors);
        List<MapBoundary> nextBoundaries = List.copyOf(boundaries == null ? List.of() : boundaries);
        if (nextObstacles.stream().anyMatch(position -> !state.map().grid().contains(position))) throw new IllegalArgumentException("obstacles must be inside grid");
        if (nextDoors.stream().anyMatch(door -> !state.map().grid().contains(door.position()))) throw new IllegalArgumentException("doors must be inside grid");
        if (nextDoors.stream().map(Door::position).anyMatch(nextObstacles::contains)) throw new IllegalArgumentException("door cannot be an obstacle");
        if (nextBoundaries.stream().anyMatch(boundary -> !boundary.inside(state.map().grid()))) throw new IllegalArgumentException("map boundary must be inside grid edges");
        if (nextBoundaries.stream().map(boundary -> boundary.x() + "," + boundary.y() + "," + boundary.orientation()).distinct().count() != nextBoundaries.size()) throw new IllegalArgumentException("map boundary must be unique");
        GridPosition player = state.map().tokens().stream().filter(token -> token.type() == TokenType.PLAYER).map(CombatToken::position).findFirst().orElse(null);
        if (player != null && nextObstacles.contains(player)) throw new IllegalArgumentException("player start cell is blocked");
        if (crop != null && !crop.isBlank()) {
            PlayerMapImageService.validateCrop(MapGridAlignmentService.mapImage(state.map()), crop);
        }
        List<MapLayer> layers = new ArrayList<>(state.map().layers().stream().filter(layer -> !Set.of("MAP_CROP", "MAP_BOUNDARIES", "MAP_LAYOUT_CONFIRMED", "MAP_BOUNDARY_CANDIDATES").contains(layer.type())).toList());
        if (crop != null && !crop.isBlank()) layers.add(new MapLayer("MAP_CROP", crop.trim(), LayerVisibility.PLAYER_VISIBLE));
        if (!nextBoundaries.isEmpty()) layers.add(new MapLayer("MAP_BOUNDARIES", nextBoundaries.stream().map(MapBoundary::encoded).sorted().collect(java.util.stream.Collectors.joining(";")), LayerVisibility.PLAYER_VISIBLE));
        layers.add(new MapLayer("MAP_LAYOUT_CONFIRMED", layoutConfirmationValue(id), LayerVisibility.PLAYER_VISIBLE));
        CombatMap updated = new CombatMap(state.map().id(), state.map().adventureId(), state.map().ruleSetId(), state.map().grid(),
                state.map().ownerPlayerId(), state.map().tokens(), nextObstacles, layers, expectedVersion + 1, commandId, fingerprint);
        updated.replaceDoors(nextDoors);
        updated.replaceRuntimeState(state.map().runtimeState());
        updated.refreshVisibility(state.map().visibilitySnapshot() == null ? 0 : state.map().visibilitySnapshot().ruleTurn());
        store.update(owner, updated, expectedVersion, expectedVersion + 1, commandId, fingerprint);
        return updated;
    }

    private String layoutConfirmationValue(MapId id) {
        if (alignments == null) return "USER";
        long version = alignments.find(id).map(MapGridAlignment::version).orElse(0L);
        return "USER|ALIGNMENT_VERSION=" + version;
    }

    public CombatMap calibrateGrid(MapId id, MapOwnerId owner, long expectedVersion, GridCalibrationRequest request) {
        VersionedOwnedCombatMap state = owned(id, owner);
        if (state.version() != expectedVersion) throw new IllegalStateException("version mismatch");
        CombatMap current = state.map();
        GridSpec nextGrid = new GridSpec(request.width(), request.height(), request.cellSize(), current.grid().distanceUnit());
        GridPosition oldPlayer = current.tokens().stream().filter(token -> token.type() == TokenType.PLAYER)
                .map(CombatToken::position).findFirst().orElse(null);
        GridPosition nextPlayer = request.playerX() == null
                ? scale(oldPlayer, current.grid(), nextGrid)
                : new GridPosition(request.playerX(), request.playerY());
        List<CombatToken> tokens = current.tokens().stream().map(token ->
                token.type() == TokenType.PLAYER
                        ? copy(token, nextPlayer)
                        : copy(token, scale(token.position(), current.grid(), nextGrid))).toList();
        Set<GridPosition> obstacles = current.obstacles().stream().map(position -> scale(position, current.grid(), nextGrid)).collect(Collectors.toSet());
        List<Door> doors = current.doors().stream().map(door -> new Door(scale(door.position(), current.grid(), nextGrid), door.open())).toList();
        if (nextPlayer == null || obstacles.contains(nextPlayer) || doors.stream().anyMatch(door -> !door.open() && door.position().equals(nextPlayer))) {
            throw new IllegalArgumentException("player start cell is blocked");
        }
        List<MapLayer> layers = new ArrayList<>(current.layers().stream()
                .filter(layer -> !Set.of("GRID_BOUNDS", "GRID_SOURCE", "GRID_META").contains(layer.type())).toList());
        layers.add(new MapLayer("GRID_BOUNDS", request.originX() + "," + request.originY() + ","
                + request.width() * request.cellSize() + "," + request.height() * request.cellSize() + ","
                + request.imageWidth() + "," + request.imageHeight(), LayerVisibility.PLAYER_VISIBLE));
        layers.add(new MapLayer("GRID_SOURCE", "MANUAL", LayerVisibility.PLAYER_VISIBLE));
        layers.add(new MapLayer("GRID_META", "source=MANUAL;origin=" + request.originX() + "," + request.originY(), LayerVisibility.AI_ONLY));
        CombatMap calibrated = new CombatMap(current.id(), current.adventureId(), current.ruleSetId(), nextGrid,
                current.ownerPlayerId(), tokens, obstacles, layers, expectedVersion + 1, UUID.randomUUID(),
                "CALIBRATE|" + request);
        calibrated.replaceDoors(doors);
        calibrated.replaceRuntimeState(current.runtimeState());
        calibrated.refreshVisibility(current.visibilitySnapshot() == null ? 0 : current.visibilitySnapshot().ruleTurn());
        store.update(owner, calibrated, expectedVersion, expectedVersion + 1, calibrated.operationKey(), calibrated.operationFingerprint());
        return calibrated;
    }

    /** 사용자가 요청한 시점의 저장된 격자와 이미지로 벽·문 초안 후보를 만든다. */
    public BoundaryDraft proposeBoundaries(MapId id, MapOwnerId owner, MapGridAlignment alignment) {
        VersionedOwnedCombatMap state = owned(id, owner);
        if (alignment == null || !alignment.mapId().equals(id)) throw new IllegalArgumentException("map grid alignment is required");
        Optional<MapImageEvidence> image = imageEvidence(state.map());
        if (image.isEmpty()) throw new MapGridAlignmentImageUnavailableException();
        GridPosition player = state.map().tokens().stream().filter(token -> token.type() == TokenType.PLAYER)
                .map(CombatToken::position).findFirst().orElse(null);
        PreparedMapData generated = aiPort.generate(new MapGenerationRequest(
                "지도 이미지의 벽과 문 초안", "GRID_CONFIRMED; 사용자가 확정한 격자와 자르기 범위를 기준으로 벽과 문만 찾는다",
                state.map().grid().width(), state.map().grid().height(), state.map().grid().cellSize(),
                state.map().grid().distanceUnit(), state.map().obstacles(), state.map().doors().stream().toList(), player, image.get(),
                alignment.originX(), alignment.originY(), alignment.cellSize(), crop(state.map()),
                alignment.imageRevision(), state.map().boundaries()));
        if (alignments != null) {
            MapGridAlignment current = alignments.find(id).orElse(alignment);
            if (current.version() != alignment.version() || !current.imageRevision().equals(alignment.imageRevision())
                    || Double.compare(current.originX(), alignment.originX()) != 0
                    || Double.compare(current.originY(), alignment.originY()) != 0
                    || Double.compare(current.cellSize(), alignment.cellSize()) != 0) {
                throw new MapGridAlignmentConflictException();
            }
        }
        Set<MapBoundary> boundaries = generated.layers().stream().filter(layer -> layer.type().equals("MAP_BOUNDARIES"))
                .flatMap(layer -> Arrays.stream(layer.value().split(";"))).filter(value -> !value.isBlank())
                .map(MapBoundary::parse).collect(Collectors.toCollection(LinkedHashSet::new));
        return new BoundaryDraft(state.version(), generated.obstacles(), generated.doors(), boundaries,
                crop(state.map()), generated.candidates(), alignment.version(), alignment.imageRevision());
    }

    /** 기존 호출부 호환용. 새 준비 흐름은 후보를 먼저 화면에 보여준다. */
    public CombatMap redraftAfterAlignment(MapId id, MapOwnerId owner, MapGridAlignment alignment) {
        BoundaryDraft draft = proposeBoundaries(id, owner, alignment);
        return updateLayout(id, owner, draft.mapVersion(), UUID.randomUUID(), draft.obstacles(), draft.doors(), draft.boundaries(), draft.crop());
    }

    /** 기존 호출부 호환용. 새 흐름에서는 반드시 저장된 정렬을 전달한다. */
    public CombatMap redraftAfterAlignment(MapId id, MapOwnerId owner) {
        VersionedOwnedCombatMap state = owned(id, owner);
        String bounds = state.map().layers().stream().filter(layer -> layer.type().equals("GRID_BOUNDS")).map(MapLayer::value).findFirst().orElse("");
        String[] values = bounds.split(",", -1);
        double originX = values.length > 0 ? parseDouble(values[0], 0) : 0;
        double originY = values.length > 1 ? parseDouble(values[1], 0) : 0;
        double cell = values.length > 2 ? parseDouble(values[2], state.map().grid().width() * state.map().grid().cellSize()) / Math.max(1, state.map().grid().width()) : state.map().grid().cellSize();
        return redraftAfterAlignment(id, owner, new MapGridAlignment(id, "legacy", originX, originY, Math.max(.01, cell), 0));
    }

    private static String crop(CombatMap map) {
        return map.layers().stream().filter(layer -> layer.type().equals("MAP_CROP")).map(MapLayer::value).findFirst().orElse("");
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (RuntimeException ignored) { return fallback; }
    }

    public record BoundaryDraft(long mapVersion, Set<GridPosition> obstacles, List<Door> doors,
            Set<MapBoundary> boundaries, String crop, List<MapBoundaryCandidate> candidates,
            long alignmentVersion, String imageRevision) {
        public BoundaryDraft(long mapVersion, Set<GridPosition> obstacles, List<Door> doors,
                Set<MapBoundary> boundaries, String crop) {
            this(mapVersion, obstacles, doors, boundaries, crop, List.of(), 0, "");
        }
        public BoundaryDraft(long mapVersion, Set<GridPosition> obstacles, List<Door> doors,
                Set<MapBoundary> boundaries, String crop, List<MapBoundaryCandidate> candidates) {
            this(mapVersion, obstacles, doors, boundaries, crop, candidates, 0, "");
        }
    }

    private static Optional<MapImageEvidence> imageEvidence(CombatMap map) {
        String value = map.layers().stream().filter(layer -> layer.type().equals("MAP_IMAGE")).map(MapLayer::value).findFirst().orElse("");
        int marker = value.indexOf(";base64,");
        if (!value.startsWith("data:image/") || marker < 0) return Optional.empty();
        try { return Optional.of(new MapImageEvidence(value.substring(5, marker), Base64.getDecoder().decode(value.substring(marker + 8)))); }
        catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    private static GridPosition scale(GridPosition position, GridSpec from, GridSpec to) {
        if (position == null) return null;
        return new GridPosition(Math.min(to.width() - 1, Math.max(0, Math.round(position.x() * (to.width() - 1f) / Math.max(1, from.width() - 1)))),
                Math.min(to.height() - 1, Math.max(0, Math.round(position.y() * (to.height() - 1f) / Math.max(1, from.height() - 1)))));
    }
    public CombatMap onGameTimeAdvanced(MapId id, MapOwnerId owner, long expectedVersion, GameTimeAdvanced event) {
        VersionedOwnedCombatMap state=owned(id, owner);
        if(!state.map().adventureId().value().equals(event.adventureId())) throw new IllegalArgumentException("game time event belongs to another adventure");
        String fingerprint=id+"|"+owner+"|TIME|"+event.adventureId()+"|"+event.ruleTurn(); CombatMap replay=replay(id,owner,event.causeId(),fingerprint); if(replay!=null)return replay;
        if(state.version()!=expectedVersion) throw new IllegalStateException("version mismatch");
        if(state.map().visibilitySnapshot()!=null && event.ruleTurn()<state.map().visibilitySnapshot().ruleTurn()) throw new IllegalArgumentException("game time must be monotonic");
        state.map().refreshVisibility(event.ruleTurn()); store.update(owner,state.map(),expectedVersion,expectedVersion+1,event.causeId(),fingerprint); return state.map();
    }
    public CombatMap applyTacticalTrigger(MapId id, MapOwnerId owner, long expectedVersion, UUID commandId,
            TacticalTriggerEffect effect) {
        String fingerprint = id + "|" + owner + "|TRIGGER|" + effect;
        CombatMap replay = replay(id, owner, commandId, fingerprint);
        if (replay != null) return replay;
        VersionedOwnedCombatMap state = owned(id, owner);
        if (state.version() != expectedVersion) throw new IllegalStateException("version mismatch");
        CombatMap updated = state.map().apply(effect);
        updated.markPersisted(expectedVersion + 1, commandId, fingerprint);
        store.update(owner, updated, expectedVersion, expectedVersion + 1, commandId, fingerprint);
        return updated;
    }
    private PlayerCombatMapView projection(CombatMap map, long version) {
        VisibilitySnapshot visibility = map.visibilitySnapshot();
        if (visibility == null || (visibility.current().isEmpty() && !playerOrigins(map).isEmpty())) {
            return failClosedProjection(map, version);
        }
        Set<GridPosition> explored = PlayerSafeFogProjection.filter(visibility.explored(), map.layers());
        Set<GridPosition> current = PlayerSafeFogProjection.filter(visibility.current(), map.layers());
        Set<TokenId> visible = visibility.observedTokens(); Set<TokenId> lastSeenIds=new HashSet<>();
        List<CombatToken> exposed = new ArrayList<>(map.tokens().stream()
                .filter(token -> visible.contains(token.id()) && (token.type() == TokenType.PLAYER || current.contains(token.position())))
                .toList());
        for (LastSeenState last : visibility.lastSeen())
            if (!visible.contains(last.tokenId()) && explored.contains(last.position())) {
                exposed.add(new CombatToken(last.tokenId(), last.type(), last.position(), TokenController.AI_GAME_MASTER, null));
                lastSeenIds.add(last.tokenId());
            }
        return new PlayerCombatMapView(map.id(), map.grid(), exposed, map.obstacles().stream().filter(explored::contains).collect(Collectors.toSet()), map.doors().stream().filter(door->explored.contains(door.position())).toList(), playerSafeLayers(map), current, explored, lastSeenIds, version);
    }
    private PlayerCombatMapView failClosedProjection(CombatMap map, long version) {
        Set<GridPosition> origins = playerOrigins(map);
        List<CombatToken> players = map.tokens().stream().filter(t -> t.type() == TokenType.PLAYER && origins.contains(t.position())).toList();
        return new PlayerCombatMapView(map.id(), map.grid(), players, Set.of(), List.of(),
                playerSafeLayers(map),
                origins, origins, Set.of(), version);
    }
    private CombatMap replay(MapId id,MapOwnerId owner,UUID commandId,String fingerprint){VersionedOwnedCombatMap replay=store.findByCommandId(commandId).orElse(null);if(replay==null)return null;if(!replay.map().id().equals(id)||!replay.owner().equals(owner)||!fingerprint.equals(replay.map().operationFingerprint()))throw new IllegalStateException("command id reused with different payload or owner");return replay.map();}
    private static Set<GridPosition> playerOrigins(CombatMap map) { return map.tokens().stream().filter(t -> t.type() == TokenType.PLAYER).map(CombatToken::position).collect(Collectors.toSet()); }
    private static List<MapLayer> playerSafeLayers(CombatMap map) { return map.layers().stream().filter(l -> l.visibility() == LayerVisibility.PLAYER_VISIBLE && !"MAP_IMAGE".equals(l.type())).toList(); }
    private void observePublicImage(MapId id, MapOwnerId owner, VersionedOwnedCombatMap state) { if (publicImages != null) publicImages.observe(id, owner, state.version()); }
    private VersionedOwnedCombatMap owned(MapId id, MapOwnerId owner) { VersionedOwnedCombatMap state = store.find(id).orElseThrow(CombatMapAccessDeniedException::new); if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException(); return state; }
    private static CombatToken copy(CombatToken t, GridPosition p) { return new CombatToken(t.id(), t.type(), p, t.controller(), t.ownerPlayerId().orElse(null), t.discovery()); }
}
