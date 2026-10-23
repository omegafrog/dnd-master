package com.dndmaster.combatmap.domain;
import java.util.*;
public final class CombatMap {
    private final MapId id; private final AdventureId adventureId; private final RuleSetId ruleSetId; private final GridSpec grid;
    private final PlayerId ownerPlayerId;
    private List<CombatToken> tokens; private final Set<GridPosition> obstacles; private final List<MapLayer> layers;
    private List<SpatialFeature> spatialFeatures = List.of(); private Set<Door> doors = Set.of();
    private boolean spatialPreparationBlocked;
    private long version; private UUID operationKey; private String operationFingerprint; private VisibilitySnapshot visibilitySnapshot;
    private Set<HostileObservationState> hostileObservations = Set.of();
    private TacticalRuntimeState runtimeState = TacticalRuntimeState.initial();
    public enum Formation { FRONT, MIDDLE, REAR }
    public record CombatMember(UUID characterId, Formation formation) {
        public CombatMember { Objects.requireNonNull(characterId); Objects.requireNonNull(formation); }
    }
    public record CombatGroup(UUID groupId, UUID tokenId, List<CombatMember> members) {
        public CombatGroup { Objects.requireNonNull(groupId); Objects.requireNonNull(tokenId); members = List.copyOf(members); if (members.isEmpty()) throw new IllegalArgumentException("group must have members"); }
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers) {
        this(id, adventureId, ruleSetId, grid, null, tokens, obstacles, layers, 0, null, null);
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, PlayerId ownerPlayerId, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers, long version, UUID operationKey) {
        this(id, adventureId, ruleSetId, grid, ownerPlayerId, tokens, obstacles, layers, version, operationKey, null);
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, PlayerId ownerPlayerId, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers, long version, UUID operationKey, String operationFingerprint) {
        this(id, adventureId, ruleSetId, grid, ownerPlayerId, tokens, obstacles, layers, version, operationKey, operationFingerprint, List.of());
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, PlayerId ownerPlayerId, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers, long version, UUID operationKey, String operationFingerprint, Collection<SpatialFeature> spatialFeatures) {
        this(id, adventureId, ruleSetId, grid, ownerPlayerId, tokens, obstacles, layers, version, operationKey, operationFingerprint, spatialFeatures, false);
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, PlayerId ownerPlayerId, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers, long version, UUID operationKey, String operationFingerprint, Collection<SpatialFeature> spatialFeatures, boolean spatialPreparationBlocked) {
        this.id=Objects.requireNonNull(id); this.adventureId=Objects.requireNonNull(adventureId); this.ruleSetId=Objects.requireNonNull(ruleSetId); this.grid=Objects.requireNonNull(grid);
        this.ownerPlayerId = ownerPlayerId;
        this.tokens=List.copyOf(Objects.requireNonNull(tokens)); this.obstacles=Set.copyOf(Objects.requireNonNull(obstacles)); this.layers=List.copyOf(Objects.requireNonNull(layers));
        if (tokens.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("combat map tokens must not be null");
        Set<TokenId> ids=new HashSet<>(); if(tokens.stream().anyMatch(t->!ids.add(t.id()) || !grid.contains(t.position()))) throw new IllegalArgumentException("tokens must be unique and inside grid");
        if(this.obstacles.stream().anyMatch(p->!grid.contains(p))) throw new IllegalArgumentException("obstacles must be inside grid");
        materializeSpatialFeatures(spatialFeatures);
        this.spatialPreparationBlocked = spatialPreparationBlocked;
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.operationKey = operationKey;
        this.operationFingerprint = operationFingerprint;
    }
    public void movePlayerToken(PlayerId playerId, TokenId tokenId, MovementPath path, int maximumDistance) {
        validatePlayerMovement(playerId, tokenId, path, maximumDistance);
        for (int index = 1; index < path.orderedPositions().size(); index++) {
            advancePlayerToken(playerId, tokenId, path.orderedPositions().get(index));
        }
    }
    /** Validates a whole public path without changing the aggregate. */
    public void validatePlayerMovement(PlayerId playerId, TokenId tokenId, MovementPath path, int maximumDistance) {
        CombatToken token=tokens.stream().filter(t->t.id().equals(tokenId)).findFirst().orElseThrow(()->new CombatMapMovementDeniedException("token not found"));
        if(token.type()!=TokenType.PLAYER || token.controller()!=TokenController.PLAYER || !token.ownerPlayerId().orElseThrow().equals(playerId)) throw new CombatMapMovementDeniedException("player may move only own PLAYER token");
        if(maximumDistance<0 || path.distance()>maximumDistance) throw new CombatMapMovementDeniedException("path exceeds applied-edition movement allowance");
        if(!path.orderedPositions().getFirst().equals(token.position())) throw new CombatMapMovementDeniedException("path must start at token position");
        int expectedDistance=(path.orderedPositions().size()-1)*grid.distanceUnit();
        if(path.distance()!=expectedDistance) throw new CombatMapMovementDeniedException("path distance does not match grid");
        for(int i=0;i<path.orderedPositions().size();i++){
            GridPosition position=path.orderedPositions().get(i);
            if(!isPublicTraversable(position)) throw new CombatMapMovementDeniedException("path crosses blocked or outside public position");
            if(i>0 && !publiclyTraversableBetween(path.orderedPositions().get(i - 1), position)) {
                throw new CombatMapMovementDeniedException("path crosses blocked position or wall");
            }
        }
    }
    /** Advances one already-validated adjacent cell; callers keep this private staging state until commit. */
    public void advancePlayerToken(PlayerId playerId, TokenId tokenId, GridPosition nextPosition) {
        CombatToken token=tokens.stream().filter(t->t.id().equals(tokenId)).findFirst().orElseThrow(()->new CombatMapMovementDeniedException("token not found"));
        if(token.type()!=TokenType.PLAYER || token.controller()!=TokenController.PLAYER || !token.ownerPlayerId().orElseThrow().equals(playerId)) throw new CombatMapMovementDeniedException("player may move only own PLAYER token");
        if (!token.position().adjacentTo(nextPosition) || !publiclyTraversableBetween(token.position(), nextPosition)) {
            throw new CombatMapMovementDeniedException("next movement cell is not publicly traversable");
        }
        token.moveTo(nextPosition);
    }
    public void moveEnemyToken(TokenId tokenId, MovementPath path, int maximumDistance) {
        validateEnemyMovement(tokenId, path, maximumDistance);
        CombatToken token = tokens.stream().filter(candidate -> candidate.id().equals(tokenId)).findFirst().orElseThrow();
        for (int index = 1; index < path.orderedPositions().size(); index++) token.moveTo(path.orderedPositions().get(index));
    }
    public void validateEnemyMovement(TokenId tokenId, MovementPath path, int maximumDistance) {
        CombatToken token = tokens.stream().filter(candidate -> candidate.id().equals(tokenId))
                .findFirst().orElseThrow(() -> new CombatMapMovementDeniedException("token not found"));
        if ((token.type() != TokenType.ENEMY && token.type() != TokenType.BOSS)
                || token.controller() != TokenController.AI_GAME_MASTER)
            throw new CombatMapMovementDeniedException("environment may move only an enemy token");
        if (maximumDistance < 0 || path.distance() > maximumDistance)
            throw new CombatMapMovementDeniedException("path exceeds movement allowance");
        if (!path.orderedPositions().getFirst().equals(token.position()))
            throw new CombatMapMovementDeniedException("path must start at enemy token position");
        if (path.distance() != (path.orderedPositions().size() - 1) * grid.distanceUnit())
            throw new CombatMapMovementDeniedException("path distance does not match grid");
        for (int index = 0; index < path.orderedPositions().size(); index++) {
            GridPosition position = path.orderedPositions().get(index);
            if (!isPublicTraversable(position) || index > 0
                    && !publiclyTraversableBetween(path.orderedPositions().get(index - 1), position))
                throw new CombatMapMovementDeniedException("path crosses blocked position or wall");
        }
    }
    public GridPosition playerTokenPosition(PlayerId playerId, TokenId tokenId) {
        CombatToken token = tokens.stream().filter(candidate -> candidate.id().equals(tokenId)).findFirst()
                .orElseThrow(() -> new CombatMapMovementDeniedException("token not found"));
        if (token.type() != TokenType.PLAYER || token.controller() != TokenController.PLAYER
                || !token.ownerPlayerId().orElseThrow().equals(playerId)) {
            throw new CombatMapMovementDeniedException("player may move only own PLAYER token");
        }
        return token.position();
    }
    public java.util.Optional<CombatToken> token(TokenId tokenId) {
        return tokens.stream().filter(candidate -> candidate.id().equals(Objects.requireNonNull(tokenId))).findFirst();
    }
    public List<CombatToken> enterCombat(List<CombatGroup> groups, PlayerId owner) {
        return enterCombat(groups, owner, Map.of());
    }
    public List<CombatToken> enterCombat(List<CombatGroup> groups, PlayerId owner, Map<UUID, GridPosition> suppliedPositions) {
        requireGroups(groups);
        suppliedPositions = suppliedPositions == null ? Map.of() : Map.copyOf(suppliedPositions);
        if (!Objects.equals(ownerPlayerId, owner)) throw new CombatMapMovementDeniedException("map owner mismatch");
        var sourceIds = groups.stream().map(group -> group.tokenId()).collect(java.util.stream.Collectors.toSet());
        var groupTokens = tokens.stream().filter(token -> sourceIds.contains(token.id().value())
                && token.type() == TokenType.PLAYER && token.ownerPlayerId().filter(owner::equals).isPresent()).toList();
        long ownedPlayerTokenCount = tokens.stream().filter(token -> token.type() == TokenType.PLAYER
                && token.ownerPlayerId().filter(owner::equals).isPresent()).count();
        if (groupTokens.size() != groups.size() || ownedPlayerTokenCount != groupTokens.size())
            throw new CombatMapMovementDeniedException("exploration group token set does not match map tokens");
        var assignments = new ArrayList<Map.Entry<UUID, GridPosition>>();
        Set<UUID> memberIds = groups.stream().flatMap(group -> group.members().stream()).map(CombatMember::characterId)
                .collect(java.util.stream.Collectors.toSet());
        if (!suppliedPositions.isEmpty() && !suppliedPositions.keySet().equals(memberIds))
            throw new CombatMapTransitionPlacementRequiredException();
        var occupied = new HashSet<GridPosition>();
        tokens.stream().filter(token -> !sourceIds.contains(token.id().value())).map(CombatToken::position).forEach(occupied::add);
        for (CombatGroup group : groups) {
            GridPosition origin = groupTokens.stream().filter(token -> token.id().value().equals(group.tokenId()))
                    .findFirst().orElseThrow().position();
            int[] direction = combatFacing(origin);
            Map<Formation, List<CombatMember>> rows = group.members().stream().collect(java.util.stream.Collectors.groupingBy(
                    CombatMember::formation, () -> new java.util.EnumMap<>(Formation.class), java.util.stream.Collectors.toCollection(ArrayList::new)));
            for (Formation formation : Formation.values()) {
                List<CombatMember> row = rows.getOrDefault(formation, List.of()).stream()
                        .sorted(java.util.Comparator.comparing(CombatMember::characterId)).toList();
                int depth = switch (formation) { case FRONT -> 0; case MIDDLE -> 1; case REAR -> 2; };
                for (int index = 0; index < row.size(); index++) {
                    int lateral = index == 0 ? 0 : (index % 2 == 1 ? -(index + 1) / 2 : index / 2);
                    int x = origin.x() - depth * direction[0] + lateral * -direction[1];
                    int y = origin.y() - depth * direction[1] + lateral * direction[0];
                    GridPosition cell = suppliedPositions.getOrDefault(row.get(index).characterId(), new GridPosition(x, y));
                    if (!transitionReachable(origin, cell, occupied) || !occupied.add(cell))
                        throw new CombatMapTransitionPlacementRequiredException();
                    assignments.add(Map.entry(row.get(index).characterId(), cell));
                }
            }
        }
        List<CombatToken> replacements = assignments.stream().map(entry -> new CombatToken(
                new TokenId(combatTokenIdentity(entry.getKey())),
                TokenType.PLAYER, entry.getValue(), TokenController.PLAYER, owner)).toList();
        tokens = java.util.stream.Stream.concat(tokens.stream().filter(token -> token.type() != TokenType.PLAYER
                || token.ownerPlayerId().filter(owner::equals).isEmpty()), replacements.stream()).toList();
        return replacements;
    }
    public List<CombatToken> exitCombat(List<CombatGroup> groups, PlayerId owner) {
        return exitCombat(groups, owner, Map.of());
    }
    public List<CombatToken> exitCombat(List<CombatGroup> groups, PlayerId owner, Map<UUID, GridPosition> suppliedPositions) {
        requireGroups(groups);
        suppliedPositions = suppliedPositions == null ? Map.of() : Map.copyOf(suppliedPositions);
        if (!Objects.equals(ownerPlayerId, owner)) throw new CombatMapMovementDeniedException("map owner mismatch");
        Set<UUID> memberIds = groups.stream().flatMap(group -> group.members().stream())
                .map(CombatMember::characterId)
                .collect(java.util.stream.Collectors.toSet());
        var players = tokens.stream().filter(token -> token.type() == TokenType.PLAYER
                && token.ownerPlayerId().filter(owner::equals).isPresent()).toList();
        Set<UUID> tokenIds = memberIds.stream().map(CombatMap::combatTokenIdentity).collect(java.util.stream.Collectors.toSet());
        if (players.size() != tokenIds.size()
                || !players.stream().map(token -> token.id().value()).collect(java.util.stream.Collectors.toSet()).equals(tokenIds))
            throw new CombatMapTransitionPlacementRequiredException();
        Set<UUID> groupIds = groups.stream().map(CombatGroup::groupId).collect(java.util.stream.Collectors.toSet());
        if (!suppliedPositions.isEmpty() && !suppliedPositions.keySet().equals(groupIds))
            throw new CombatMapTransitionPlacementRequiredException();
        var occupied = tokens.stream().filter(token -> token.type() != TokenType.PLAYER
                || token.ownerPlayerId().filter(owner::equals).isEmpty()).map(CombatToken::position)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        var restored = new ArrayList<CombatToken>();
        for (CombatGroup group : groups) {
            GridPosition position;
            if (!suppliedPositions.isEmpty()) {
                CombatMember source = group.members().stream().min(java.util.Comparator.comparing(CombatMember::characterId))
                        .orElseThrow(CombatMapTransitionPlacementRequiredException::new);
                CombatToken sourceToken = players.stream().filter(candidate -> candidate.id().value().equals(combatTokenIdentity(source.characterId())))
                        .findFirst().orElseThrow(CombatMapTransitionPlacementRequiredException::new);
                position = suppliedPositions.get(group.groupId());
                if (!transitionReachable(sourceToken.position(), position, occupied))
                    throw new CombatMapTransitionPlacementRequiredException();
            } else {
                CombatMember front = group.members().stream().filter(member -> member.formation() == Formation.FRONT)
                        .min(java.util.Comparator.comparing(CombatMember::characterId)).orElseThrow(CombatMapTransitionPlacementRequiredException::new);
                CombatToken token = players.stream().filter(candidate -> candidate.id().value().equals(combatTokenIdentity(front.characterId())))
                        .findFirst().orElseThrow(CombatMapTransitionPlacementRequiredException::new);
                position = token.position();
                if (!transitionCell(position)) throw new CombatMapTransitionPlacementRequiredException();
            }
            if (!occupied.add(position)) throw new CombatMapTransitionPlacementRequiredException();
            restored.add(new CombatToken(new TokenId(group.tokenId()), TokenType.PLAYER, position, TokenController.PLAYER, owner));
        }
        tokens = java.util.stream.Stream.concat(tokens.stream().filter(token -> !(token.type() == TokenType.PLAYER
                && token.ownerPlayerId().filter(owner::equals).isPresent())), restored.stream()).toList();
        return List.copyOf(restored);
    }
    private static void requireGroups(List<CombatGroup> groups) {
        Objects.requireNonNull(groups);
        Set<UUID> groupIds = new HashSet<>(), tokenIds = new HashSet<>(), memberIds = new HashSet<>();
        for (CombatGroup group : groups) {
            if (!groupIds.add(group.groupId()) || !tokenIds.add(group.tokenId())) throw new IllegalArgumentException("group identities must be unique");
            for (CombatMember member : group.members()) if (!memberIds.add(member.characterId())) throw new IllegalArgumentException("character may belong to only one group");
        }
    }
    private int[] combatFacing(GridPosition origin) {
        CombatToken enemy = tokens.stream().filter(token -> token.type() == TokenType.ENEMY || token.type() == TokenType.BOSS)
                .min(java.util.Comparator.<CombatToken>comparingInt(token -> GridDistance.cellsBetween(origin, 0,
                                token.position(), token.elevation(), grid.distanceUnit()))
                        .thenComparing(token -> token.id().value())).orElse(null);
        if (enemy == null) return new int[] {0, -1};
        int dx = Integer.compare(enemy.position().x(), origin.x()), dy = Integer.compare(enemy.position().y(), origin.y());
        return dx == 0 && dy == 0 ? new int[] {0, -1} : new int[] {dx, dy};
    }
    private static UUID combatTokenIdentity(UUID characterId) {
        return UUID.nameUUIDFromBytes(("player-" + characterId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private boolean transitionReachable(GridPosition origin, GridPosition target, Set<GridPosition> occupied) {
        if (!transitionCell(target)) return false;
        Set<GridPosition> visited = new HashSet<>(Set.of(origin));
        var queue = new java.util.ArrayDeque<GridPosition>(); queue.add(origin);
        while (!queue.isEmpty()) {
            GridPosition current = queue.remove();
            if (current.equals(target)) return true;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                GridPosition next = new GridPosition(current.x() + dx, current.y() + dy);
                if (!occupied.contains(next) && transitionCell(next) && transitionBetween(current, next) && visited.add(next)) queue.add(next);
            }
        }
        return false;
    }
    private boolean transitionCell(GridPosition cell) {
        return grid.contains(cell) && isPlayable(cell) && !obstacles.contains(cell)
                && doors.stream().noneMatch(door -> door.position().equals(cell) && !door.open());
    }
    private boolean transitionBetween(GridPosition from, GridPosition to) {
        if (!from.adjacentTo(to) || publicBoundaries().stream().anyMatch(boundary -> boundary.blocks(from, to))) return false;
        return from.x() == to.x() || from.y() == to.y()
                || transitionCell(new GridPosition(to.x(), from.y())) || transitionCell(new GridPosition(from.x(), to.y()));
    }
    public List<CombatToken> ensurePlayerTokenCount(PlayerId playerId, int count) {
        List<UUID> preferred = tokens.stream().filter(token -> token.type() == TokenType.PLAYER
                && token.ownerPlayerId().filter(playerId::equals).isPresent()).limit(Math.max(0, count))
                .map(token -> token.id().value()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        while (preferred.size() < count) preferred.add(null);
        return ensurePlayerTokenCount(playerId, preferred, null, count);
    }
    public List<CombatToken> ensurePlayerTokenCount(PlayerId playerId, List<UUID> preferredTokenIds, UUID originTokenId, int count) {
        Objects.requireNonNull(playerId, "player id must not be null");
        if (ownerPlayerId == null || !ownerPlayerId.equals(playerId) || count < 1 || preferredTokenIds == null || preferredTokenIds.size() != count)
            throw new CombatMapMovementDeniedException("invalid exploration token owner or count");
        if (runtimeState.combatEntered() && runtimeState.outcome().isBlank())
            throw new CombatMapMovementDeniedException("exploration tokens cannot replace player tokens during active combat");
        List<CombatToken> playerTokens = tokens.stream().filter(token -> token.type() == TokenType.PLAYER
                && token.ownerPlayerId().filter(playerId::equals).isPresent()).toList();
        if (playerTokens.isEmpty()) throw new CombatMapMovementDeniedException("player token position is unavailable");
        Set<UUID> seen = new HashSet<>();
        List<CombatToken> ensured = new ArrayList<>(count);
        for (UUID preferredId : preferredTokenIds) {
            if (preferredId == null) { ensured.add(null); continue; }
            if (!seen.add(preferredId)) throw new CombatMapMovementDeniedException("exploration token ids must be unique");
            ensured.add(playerTokens.stream().filter(candidate -> candidate.id().value().equals(preferredId)).findFirst()
                    .orElseThrow(() -> new CombatMapMovementDeniedException("exploration token does not belong to player")));
        }
        CombatToken origin = originTokenId == null ? playerTokens.getFirst() : playerTokens.stream()
                .filter(candidate -> candidate.id().value().equals(originTokenId)).findFirst()
                .orElseThrow(() -> new CombatMapMovementDeniedException("exploration origin token does not belong to player"));
        GridPosition spawn = origin.position();
        if (!isPublicTraversable(spawn)) throw new CombatMapMovementDeniedException("player token position is not publicly traversable");
        for (int index = 0; index < ensured.size(); index++) if (ensured.get(index) == null)
            ensured.set(index, new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER, spawn, TokenController.PLAYER, playerId));
        tokens = java.util.stream.Stream.concat(tokens.stream().filter(token -> token.type() != TokenType.PLAYER
                        || !token.ownerPlayerId().filter(playerId::equals).isPresent()), ensured.stream())
                .toList();
        return List.copyOf(ensured);
    }
    public int setTokenElevation(TokenId tokenId, int elevationFeet) {
        CombatToken token = token(tokenId).orElseThrow(() -> new CombatMapMovementDeniedException("token not found"));
        token.setElevation(elevationFeet);
        return token.elevation();
    }
    public List<CombatToken> tokensAt(Set<GridPosition> cells) {
        Objects.requireNonNull(cells, "token cells must not be null");
        return tokens.stream().filter(token -> cells.contains(token.position())).toList();
    }
    public boolean isPublicTraversable(GridPosition position) {
        boolean known = visibilitySnapshot != null
                && (visibilitySnapshot.current().contains(position) || visibilitySnapshot.explored().contains(position));
        return known && grid.contains(position) && isPlayable(position) && !obstacles.contains(position)
                && doors.stream().noneMatch(door -> door.position().equals(position) && !door.open());
    }
    public boolean publiclyTraversableBetween(GridPosition from, GridPosition to) {
        if (!from.adjacentTo(to) || !isPublicTraversable(from) || !isPublicTraversable(to)
                || publicBoundaries().stream().anyMatch(boundary -> boundary.blocks(from, to))) return false;
        boolean diagonal = from.x() != to.x() && from.y() != to.y();
        if (!diagonal) return true;
        GridPosition horizontal = new GridPosition(to.x(), from.y());
        GridPosition vertical = new GridPosition(from.x(), to.y());
        return isPublicTraversable(horizontal) || isPublicTraversable(vertical);
    }
    public void markPersisted(long version, UUID operationKey, String operationFingerprint) {
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.operationKey = operationKey;
        this.operationFingerprint = operationFingerprint;
    }
    public MapId id(){return id;} public AdventureId adventureId(){return adventureId;} public RuleSetId ruleSetId(){return ruleSetId;}
    public GridSpec grid(){return grid;} public PlayerId ownerPlayerId(){return ownerPlayerId;} public List<CombatToken> tokens(){return tokens;} public Set<GridPosition> obstacles(){return obstacles;} public List<MapLayer> layers(){return layers;} public List<SpatialFeature> spatialFeatures(){return spatialFeatures;}
    public boolean spatialPreparationBlocked(){return spatialPreparationBlocked;}
    public void blockSpatialPreparation(){spatialPreparationBlocked = true;}
    public void completeSpatialPreparation(){spatialPreparationBlocked = false;}
    public long version(){return version;} public UUID operationKey(){return operationKey;} public String operationFingerprint(){return operationFingerprint;}
    public VisibilitySnapshot visibilitySnapshot(){return visibilitySnapshot;}
    public TacticalRuntimeState runtimeState(){return runtimeState;}
    public void replaceRuntimeState(TacticalRuntimeState state){runtimeState=Objects.requireNonNull(state);}
    public void replaceVisibility(VisibilitySnapshot snapshot){visibilitySnapshot=Objects.requireNonNull(snapshot);}
    public Set<Door> doors(){return doors;}
    public boolean isPlayable(GridPosition position) { return PlayableMapArea.contains(grid, layers, position); }
    public Set<MapBoundary> boundaries() {
        return layers.stream().filter(layer -> layer.type().equals("MAP_BOUNDARIES"))
                .flatMap(layer -> Arrays.stream(layer.value().split(";"))).filter(value -> !value.isBlank())
                .map(MapBoundary::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public Set<MapBoundary> publicBoundaries() {
        return layers.stream().filter(layer -> layer.type().equals("MAP_BOUNDARIES") && layer.visibility() == LayerVisibility.PLAYER_VISIBLE)
                .flatMap(layer -> Arrays.stream(layer.value().split(";"))).filter(value -> !value.isBlank())
                .map(MapBoundary::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public void replaceDoors(Collection<Door> nextDoors){
        Objects.requireNonNull(nextDoors);
        if(nextDoors.stream().anyMatch(door -> !grid.contains(door.position()))) throw new IllegalArgumentException("doors must be inside grid");
        doors=Set.copyOf(nextDoors);
    }
    /** Validate the complete batch before changing the aggregate. */
    public void materializeSpatialFeatures(Collection<SpatialFeature> features) {
        Objects.requireNonNull(features, "spatial features must not be null");
        List<SpatialFeature> candidate = List.copyOf(features);
        Set<UUID> ids = new HashSet<>();
        Set<GridPosition> explored = visibilitySnapshot == null ? Set.of() : visibilitySnapshot.explored();
        for (SpatialFeature feature : candidate) {
            if (feature == null || !ids.add(feature.id())) throw new IllegalArgumentException("spatial feature ids must be unique");
            if (feature.cells().stream().anyMatch(cell -> !grid.contains(cell))) throw new IllegalArgumentException("spatial feature cell is outside grid");
            if (feature.provenance().origin() == SpatialFeatureOrigin.STORY_PLAN
                    && feature.visibility() == SpatialFeatureVisibility.HIDDEN
                    && feature.cells().stream().anyMatch(explored::contains)) {
                throw new IllegalArgumentException("hidden story-plan feature cannot be added to an explored cell");
            }
        }
        spatialFeatures = List.copyOf(candidate);
    }
    public void addRuntimeSpatialFeature(SpatialFeature feature) {
        Objects.requireNonNull(feature, "spatial feature must not be null");
        if (feature.provenance().origin() == SpatialFeatureOrigin.STORY_PLAN) throw new IllegalArgumentException("runtime additions require runtime provenance");
        List<SpatialFeature> next = new ArrayList<>(spatialFeatures); next.add(feature); materializeSpatialFeatures(next);
    }
    public void refreshVisibility(long ruleTurn){
        Set<GridPosition> origins=tokens.stream().filter(t->t.type()==TokenType.PLAYER).map(CombatToken::position).collect(java.util.stream.Collectors.toSet());
        Set<GridPosition> blocked=new HashSet<>(obstacles); doors.stream().filter(d->!d.open()).map(Door::position).forEach(blocked::add);
        VisibilitySnapshot prior=visibilitySnapshot;
        origins.removeIf(position -> !isPlayable(position));
        for (int y = 0; y < grid.height(); y++) for (int x = 0; x < grid.width(); x++) {
            GridPosition position = new GridPosition(x, y);
            if (!isPlayable(position)) blocked.add(position);
        }
        VisibilitySnapshot calculated = new VisibilityPolicy().calculate(grid,origins,prior==null?Set.of():prior.explored(),blocked,doors,boundaries(),tokens,prior==null?Set.of():prior.lastSeen(),ruleTurn);
        Set<GridPosition> current = calculated.current().stream().filter(this::isPlayable).collect(java.util.stream.Collectors.toSet());
        Set<GridPosition> explored = calculated.explored().stream().filter(this::isPlayable).collect(java.util.stream.Collectors.toSet());
        Set<TokenId> observed = calculated.observedTokens().stream().filter(id -> tokens.stream()
                .anyMatch(token -> token.id().equals(id) && current.contains(token.position()))).collect(java.util.stream.Collectors.toSet());
        visibilitySnapshot = new VisibilitySnapshot(current, explored, observed,
                calculated.lastSeen().stream().filter(last -> isPlayable(last.position())).toList(), ruleTurn);
    }
    public HostileObservationStatus hostileObservationStatus(TokenId hostileTokenId, TokenId playerTokenId) {
        return hostileObservations.stream()
                .filter(value -> value.hostileTokenId().equals(hostileTokenId) && value.playerTokenId().equals(playerTokenId))
                .map(HostileObservationState::status).findFirst().orElse(null);
    }
    public Set<HostileObservationState> hostileObservations() { return Set.copyOf(hostileObservations); }
    public void replaceHostileObservations(Collection<HostileObservationState> values) {
        hostileObservations = Set.copyOf(Objects.requireNonNull(values, "hostile observations must not be null"));
    }
    /** Copies map-owned control state when an application operation rebuilds the aggregate. */
    public void preserveControlStateFrom(CombatMap source) {
        Objects.requireNonNull(source, "source combat map must not be null");
        replaceDoors(source.doors());
        replaceRuntimeState(source.runtimeState());
        if (source.visibilitySnapshot() != null) replaceVisibility(source.visibilitySnapshot());
        replaceHostileObservations(source.hostileObservations());
    }
    public void markHostileAware(TokenId hostileTokenId, TokenId playerTokenId) {
        replaceHostileObservation(new HostileObservationState(hostileTokenId, playerTokenId, HostileObservationStatus.AWARE));
    }
    public void markHostileLost(TokenId hostileTokenId, TokenId playerTokenId) {
        replaceHostileObservation(new HostileObservationState(hostileTokenId, playerTokenId, HostileObservationStatus.LOST));
    }
    private void replaceHostileObservation(HostileObservationState state) {
        Set<HostileObservationState> next = new HashSet<>(hostileObservations);
        next.removeIf(value -> value.hostileTokenId().equals(state.hostileTokenId()) && value.playerTokenId().equals(state.playerTokenId()));
        next.add(state);
        hostileObservations = Set.copyOf(next);
    }
    public CombatMap apply(com.dndmaster.combatmap.application.view.TacticalTriggerEffect effect) {
        if (!effect.planned()) throw new IllegalArgumentException("only planned tactical triggers may change the map");
        List<String> tokenTargetIds = effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.FOG_REVEAL
                ? effect.targetIds().stream().filter(value -> value == null || !value.matches("\\d+,\\d+")).toList()
                : effect.targetIds();
        Set<UUID> targets = tokenTargetIds.stream()
                .map(CombatMap::canonicalTokenId)
                .collect(java.util.stream.Collectors.toSet());
        if (!targets.isEmpty() && tokens.stream().map(t -> t.id().value()).collect(java.util.stream.Collectors.toSet()).containsAll(targets) == false)
            throw new IllegalArgumentException("tactical trigger targets are not present on the map");
        TokenDiscovery targetDiscovery = effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.REWARD
                || effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.BOSS
                ? TokenDiscovery.REVEALED : TokenDiscovery.DISCOVERED;
        List<CombatToken> nextTokens = tokens.stream().map(token -> targets.contains(token.id().value())
                ? new CombatToken(token.id(), token.type(), token.position(), token.controller(), token.ownerPlayerId().orElse(null),
                        token.discovery() == TokenDiscovery.REVEALED ? TokenDiscovery.REVEALED : targetDiscovery,
                        token.hostileObservationRule().orElse(null), token.elevation()) : token).toList();
        List<MapLayer> nextLayers = new ArrayList<>(layers);
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.FOG_REVEAL) {
            nextLayers.replaceAll(layer -> layer.type().equals("INITIAL_FOG") ? revealFog(layer, effect.targetIds(), tokens) : layer);
            nextLayers.removeIf(layer -> layer.type().equals("INITIAL_FOG") && (layer.value().isBlank() || layer.value().equals("cleared")));
        }
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.REWARD) nextLayers.add(new MapLayer("RESOLVED_REWARD", effect.triggerId(), LayerVisibility.PLAYER_VISIBLE));
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.ALARM) nextLayers.add(new MapLayer("ALARM", effect.triggerId(), LayerVisibility.PLAYER_VISIBLE));
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.COMBAT_ENTRY) nextLayers.add(new MapLayer("COMBAT_ENTRY", effect.triggerId(), LayerVisibility.PLAYER_VISIBLE));
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.REINFORCEMENT) nextLayers.add(new MapLayer("REINFORCEMENT", effect.triggerId(), LayerVisibility.PLAYER_VISIBLE));
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.BOSS) nextLayers.add(new MapLayer("BOSS_TRANSITION", effect.triggerId(), LayerVisibility.PLAYER_VISIBLE));
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.SURRENDER) nextLayers.add(new MapLayer("SURRENDER", effect.triggerId(), LayerVisibility.PLAYER_VISIBLE));
        if (!effect.transitionId().isBlank()) nextLayers.add(new MapLayer("TACTICAL_TRANSITION", effect.transitionId(), LayerVisibility.PLAYER_VISIBLE));
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.SUCCESS || effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.FAILURE || effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.EXIT)
            nextLayers.add(new MapLayer("TACTICAL_OUTCOME", effect.kind().name(), LayerVisibility.PLAYER_VISIBLE));
        CombatMap next = new CombatMap(id, adventureId, ruleSetId, grid, ownerPlayerId, nextTokens, obstacles, nextLayers, version + 1, null, null, spatialFeatures);
        next.preserveControlStateFrom(this);
        TacticalRuntimeState state = runtimeState;
        state = switch (effect.kind()) {
            case COMBAT_ENTRY -> new TacticalRuntimeState(true, state.alarmRaised(), state.reinforcementsActivated(), state.bossActivated(), state.rewardDiscovered(), state.outcome(), state.transitionId());
            case ALARM -> new TacticalRuntimeState(state.combatEntered(), true, state.reinforcementsActivated(), state.bossActivated(), state.rewardDiscovered(), state.outcome(), state.transitionId());
            case REINFORCEMENT -> new TacticalRuntimeState(state.combatEntered(), state.alarmRaised(), true, state.bossActivated(), state.rewardDiscovered(), state.outcome(), state.transitionId());
            case BOSS -> new TacticalRuntimeState(state.combatEntered(), state.alarmRaised(), state.reinforcementsActivated(), true, state.rewardDiscovered(), state.outcome(), state.transitionId());
            case REWARD -> new TacticalRuntimeState(state.combatEntered(), state.alarmRaised(), state.reinforcementsActivated(), state.bossActivated(), true, state.outcome(), state.transitionId());
            case SUCCESS, FAILURE, EXIT, SURRENDER -> new TacticalRuntimeState(state.combatEntered(), state.alarmRaised(), state.reinforcementsActivated(), state.bossActivated(), state.rewardDiscovered(), effect.kind().name(), effect.transitionId().isBlank() ? state.transitionId() : effect.transitionId());
            default -> state;
        };
        if (!effect.transitionId().isBlank() && !state.transitionId().equals(effect.transitionId())) state = new TacticalRuntimeState(state.combatEntered(), state.alarmRaised(), state.reinforcementsActivated(), state.bossActivated(), state.rewardDiscovered(), state.outcome(), effect.transitionId());
        next.replaceRuntimeState(state);
        if (spatialPreparationBlocked) next.blockSpatialPreparation();
        next.replaceDoors(doors); next.refreshVisibility(visibilitySnapshot == null ? 0 : visibilitySnapshot.ruleTurn());
        next.replaceHostileObservations(hostileObservations);
        if (effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.FOG_REVEAL) {
            next.revealCells(fogRevealPositions(effect.targetIds(), tokens));
        }
        return next;
    }

    private void revealCells(Collection<GridPosition> cells) {
        if (cells.isEmpty() || visibilitySnapshot == null) return;
        Set<GridPosition> current = new HashSet<>(visibilitySnapshot.current());
        Set<GridPosition> explored = new HashSet<>(visibilitySnapshot.explored());
        cells.stream().filter(grid::contains).forEach(position -> { current.add(position); explored.add(position); });
        visibilitySnapshot = new VisibilitySnapshot(current, explored, visibilitySnapshot.observedTokens(),
                visibilitySnapshot.lastSeen(), visibilitySnapshot.ruleTurn());
    }

    private static Set<GridPosition> fogRevealPositions(List<String> requested, List<CombatToken> tokens) {
        Set<GridPosition> positions = requested.stream().flatMap(value -> fogCellPosition(value).stream())
                .collect(java.util.stream.Collectors.toSet());
        for (String target : requested) {
            UUID id;
            try { id = canonicalTokenId(target); } catch (RuntimeException ignored) { continue; }
            tokens.stream().filter(token -> token.id().value().equals(id)).findFirst()
                    .ifPresent(token -> positions.add(token.position()));
        }
        return positions;
    }

    /**
     * Tactical plans use authored string ids (for example, enemy-1).  The map
     * persistence model uses UUID token ids, so both boundaries must use the
     * same deterministic canonicalization rather than parsing authored ids as
     * UUIDs.
     */
    public static UUID canonicalTokenId(String authoredId) {
        Objects.requireNonNull(authoredId, "tactical target id must not be null");
        try {
            return UUID.fromString(authoredId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(authoredId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static MapLayer revealFog(MapLayer layer, List<String> requested, List<CombatToken> tokens) {
        Set<String> cells = requested.stream().flatMap(value -> fogCell(value).stream()).collect(java.util.stream.Collectors.toSet());
        if (requested.isEmpty()) return layer;
        for (String target : requested) {
            UUID id;
            try { id = canonicalTokenId(target); } catch (RuntimeException ignored) { continue; }
            tokens.stream().filter(token -> token.id().value().equals(id)).findFirst()
                    .ifPresent(token -> cells.add(token.position().x() + "," + token.position().y()));
        }
        String remaining = Arrays.stream(layer.value().split(";"))
                .map(String::trim).filter(cell -> !cells.contains(cell)).collect(java.util.stream.Collectors.joining(";"));
        return remaining.isBlank() ? new MapLayer("INITIAL_FOG", "cleared", LayerVisibility.AI_ONLY) : new MapLayer("INITIAL_FOG", remaining, LayerVisibility.AI_ONLY);
    }

    private static Optional<String> fogCell(String value) {
        if (value != null && value.matches("\\d+,\\d+")) return Optional.of(value.trim());
        return Optional.empty();
    }

    private static Optional<GridPosition> fogCellPosition(String value) {
        return fogCell(value).map(cell -> {
            String[] coordinates = cell.split(",");
            return new GridPosition(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
        });
    }
}
