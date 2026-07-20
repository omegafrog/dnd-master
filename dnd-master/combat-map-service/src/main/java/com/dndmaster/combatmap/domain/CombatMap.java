package com.dndmaster.combatmap.domain;
import java.util.*;
public final class CombatMap {
    private final MapId id; private final AdventureId adventureId; private final RuleSetId ruleSetId; private final GridSpec grid;
    private final List<CombatToken> tokens; private final Set<GridPosition> obstacles; private final List<MapLayer> layers;
    public CombatMap(MapId id, AdventureId adventureId, RuleSetId ruleSetId, GridSpec grid, List<CombatToken> tokens, Collection<GridPosition> obstacles, List<MapLayer> layers) {
        this.id=Objects.requireNonNull(id); this.adventureId=Objects.requireNonNull(adventureId); this.ruleSetId=Objects.requireNonNull(ruleSetId); this.grid=Objects.requireNonNull(grid);
        this.tokens=List.copyOf(Objects.requireNonNull(tokens)); this.obstacles=Set.copyOf(Objects.requireNonNull(obstacles)); this.layers=List.copyOf(Objects.requireNonNull(layers));
        if (tokens.isEmpty() || tokens.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("combat map requires tokens");
        Set<TokenId> ids=new HashSet<>(); if(tokens.stream().anyMatch(t->!ids.add(t.id()) || !grid.contains(t.position()))) throw new IllegalArgumentException("tokens must be unique and inside grid");
        if(this.obstacles.stream().anyMatch(p->!grid.contains(p))) throw new IllegalArgumentException("obstacles must be inside grid");
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
    public MapId id(){return id;} public AdventureId adventureId(){return adventureId;} public RuleSetId ruleSetId(){return ruleSetId;}
    public GridSpec grid(){return grid;} public List<CombatToken> tokens(){return tokens;} public Set<GridPosition> obstacles(){return obstacles;} public List<MapLayer> layers(){return layers;}
}
