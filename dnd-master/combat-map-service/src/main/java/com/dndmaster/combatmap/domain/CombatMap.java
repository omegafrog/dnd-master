package com.dndmaster.combatmap.domain;
import java.util.*;
public final class CombatMap {
    private final MapId id; private final AdventureId adventureId; private final RuleSetId ruleSetId; private final GridSpec grid;
    private final PlayerId ownerPlayerId;
    private final List<CombatToken> tokens; private final Set<GridPosition> obstacles; private final List<MapLayer> layers;
    private long version; private UUID operationKey; private String operationFingerprint;
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers) {
        this(id, adventureId, ruleSetId, grid, null, tokens, obstacles, layers, 0, null, null);
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, PlayerId ownerPlayerId, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers, long version, UUID operationKey) {
        this(id, adventureId, ruleSetId, grid, ownerPlayerId, tokens, obstacles, layers, version, operationKey, null);
    }
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, PlayerId ownerPlayerId, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers, long version, UUID operationKey, String operationFingerprint) {
        this.id=Objects.requireNonNull(id); this.adventureId=Objects.requireNonNull(adventureId); this.ruleSetId=Objects.requireNonNull(ruleSetId); this.grid=Objects.requireNonNull(grid);
        this.ownerPlayerId = ownerPlayerId;
        this.tokens=List.copyOf(Objects.requireNonNull(tokens)); this.obstacles=Set.copyOf(Objects.requireNonNull(obstacles)); this.layers=List.copyOf(Objects.requireNonNull(layers));
        if (tokens.isEmpty() || tokens.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("combat map requires tokens");
        Set<TokenId> ids=new HashSet<>(); if(tokens.stream().anyMatch(t->!ids.add(t.id()) || !grid.contains(t.position()))) throw new IllegalArgumentException("tokens must be unique and inside grid");
        if(this.obstacles.stream().anyMatch(p->!grid.contains(p))) throw new IllegalArgumentException("obstacles must be inside grid");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.operationKey = operationKey;
        this.operationFingerprint = operationFingerprint;
    }
    public void movePlayerToken(PlayerId playerId, TokenId tokenId, MovementPath path, int maximumDistance) {
        CombatToken token=tokens.stream().filter(t->t.id().equals(tokenId)).findFirst().orElseThrow(()->new CombatMapMovementDeniedException("token not found"));
        if(token.type()!=TokenType.PLAYER || token.controller()!=TokenController.PLAYER || !token.ownerPlayerId().orElseThrow().equals(playerId)) throw new CombatMapMovementDeniedException("player may move only own PLAYER token");
        if(maximumDistance<0 || path.distance()>maximumDistance) throw new CombatMapMovementDeniedException("path exceeds applied-edition movement allowance");
        if(!path.orderedPositions().getFirst().equals(token.position())) throw new CombatMapMovementDeniedException("path must start at token position");
        int expectedDistance=(path.orderedPositions().size()-1)*grid.distanceUnit();
        if(path.distance()!=expectedDistance) throw new CombatMapMovementDeniedException("path distance does not match grid");
        for(int i=0;i<path.orderedPositions().size();i++){
            GridPosition position=path.orderedPositions().get(i);
            if(!grid.contains(position) || obstacles.contains(position)) throw new CombatMapMovementDeniedException("path crosses blocked or outside position");
            if(i>0 && !path.orderedPositions().get(i-1).adjacentTo(position)) throw new CombatMapMovementDeniedException("path positions must be adjacent");
        }
        token.moveTo(path.orderedPositions().getLast());
    }
    public void markPersisted(long version, UUID operationKey, String operationFingerprint) {
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.operationKey = operationKey;
        this.operationFingerprint = operationFingerprint;
    }
    public MapId id(){return id;} public AdventureId adventureId(){return adventureId;} public RuleSetId ruleSetId(){return ruleSetId;}
    public GridSpec grid(){return grid;} public PlayerId ownerPlayerId(){return ownerPlayerId;} public List<CombatToken> tokens(){return tokens;} public Set<GridPosition> obstacles(){return obstacles;} public List<MapLayer> layers(){return layers;}
    public long version(){return version;} public UUID operationKey(){return operationKey;} public String operationFingerprint(){return operationFingerprint;}
}
