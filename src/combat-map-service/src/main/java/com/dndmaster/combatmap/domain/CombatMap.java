package com.dndmaster.combatmap.domain;
import java.util.*;
public final class CombatMap {
    private final MapId id; private final AdventureId adventureId; private final RuleSetId ruleSetId; private final GridSpec grid;
    private final PlayerId ownerPlayerId;
    private final List<CombatToken> tokens; private final Set<GridPosition> obstacles; private final List<MapLayer> layers; private Set<Door> doors = Set.of();
    private long version; private UUID operationKey; private String operationFingerprint; private VisibilitySnapshot visibilitySnapshot;
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
    public VisibilitySnapshot visibilitySnapshot(){return visibilitySnapshot;}
    public void replaceVisibility(VisibilitySnapshot snapshot){visibilitySnapshot=Objects.requireNonNull(snapshot);}
    public Set<Door> doors(){return doors;}
    public void replaceDoors(Collection<Door> nextDoors){
        Objects.requireNonNull(nextDoors);
        if(nextDoors.stream().anyMatch(door -> !grid.contains(door.position()))) throw new IllegalArgumentException("doors must be inside grid");
        doors=Set.copyOf(nextDoors);
    }
    public void refreshVisibility(long ruleTurn){
        Set<GridPosition> origins=tokens.stream().filter(t->t.type()==TokenType.PLAYER).map(CombatToken::position).collect(java.util.stream.Collectors.toSet());
        Set<GridPosition> blocked=new HashSet<>(obstacles); doors.stream().filter(d->!d.open()).map(Door::position).forEach(blocked::add);
        VisibilitySnapshot prior=visibilitySnapshot;
        visibilitySnapshot=new VisibilityPolicy().calculate(grid,origins,prior==null?Set.of():prior.explored(),blocked,doors,tokens,prior==null?Set.of():prior.lastSeen(),ruleTurn);
    }
    public CombatMap apply(com.dndmaster.combatmap.application.view.TacticalTriggerEffect effect) {
        if (!effect.planned()) throw new IllegalArgumentException("only planned tactical triggers may change the map");
        Set<UUID> targets = effect.targetIds().stream()
                .map(CombatMap::canonicalTokenId)
                .collect(java.util.stream.Collectors.toSet());
        if (!targets.isEmpty() && tokens.stream().map(t -> t.id().value()).collect(java.util.stream.Collectors.toSet()).containsAll(targets) == false)
            throw new IllegalArgumentException("tactical trigger targets are not present on the map");
        TokenDiscovery targetDiscovery = effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.REWARD
                || effect.kind() == com.dndmaster.combatmap.application.view.TacticalTriggerEffect.Kind.BOSS
                ? TokenDiscovery.REVEALED : TokenDiscovery.DISCOVERED;
        List<CombatToken> nextTokens = tokens.stream().map(token -> targets.contains(token.id().value())
                ? new CombatToken(token.id(), token.type(), token.position(), token.controller(), token.ownerPlayerId().orElse(null),
                        token.discovery() == TokenDiscovery.REVEALED ? TokenDiscovery.REVEALED : targetDiscovery) : token).toList();
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
        CombatMap next = new CombatMap(id, adventureId, ruleSetId, grid, ownerPlayerId, nextTokens, obstacles, nextLayers, version + 1, null, null);
        next.replaceDoors(doors); next.refreshVisibility(visibilitySnapshot == null ? 0 : visibilitySnapshot.ruleTurn());
        return next;
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
}
