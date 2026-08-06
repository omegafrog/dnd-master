package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.*;
import java.util.stream.Collectors;

public final class CombatMapViewService {
    private final CombatMapViewStore store;
    private final MapFilePreparationPort filePort;
    private final AiMapGenerationPort aiPort;

    public CombatMapViewService(CombatMapViewStore store, MapFilePreparationPort filePort, AiMapGenerationPort aiPort) {
        this.store = Objects.requireNonNull(store); this.filePort = Objects.requireNonNull(filePort); this.aiPort = Objects.requireNonNull(aiPort);
    }
    public CombatMap prepareUploaded(MapOwnerId owner, AdventureId adventure, RuleSetId rules, UploadedMapSource source) { return saveNew(owner, adventure, rules, filePort.prepare(source)); }
    public CombatMap prepareGenerated(MapOwnerId owner, AdventureId adventure, RuleSetId rules, String description) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        return saveNew(owner, adventure, rules, aiPort.generate(description.trim()));
    }
    private CombatMap saveNew(MapOwnerId owner, AdventureId adventure, RuleSetId rules, PreparedMapData data) {
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), adventure, rules, data.grid(), new PlayerId(owner.value()), data.tokens(), data.obstacles(), data.layers(), 0, null);
        map.refreshVisibility(0);
        store.insert(owner, map); return map;
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
        VisibilitySnapshot prior = state.map().visibilitySnapshot();
        updated.replaceDoors(state.map().doors());
        updated.refreshVisibility(prior == null ? 0 : prior.ruleTurn() + 1);
        store.update(owner, updated, expectedVersion); return updated;
    }
    public PlayerCombatMapView displayForPlayer(MapId id, MapOwnerId owner) { VersionedOwnedCombatMap state = owned(id, owner); return projection(state.map(), state.version()); }
    public Optional<PlayerCombatMapView> displayForAdventure(AdventureId adventureId, MapOwnerId owner) { return store.findByAdventureId(adventureId).filter(state -> state.owner().equals(owner)).map(state -> projection(state.map(), state.version())); }
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
    public CombatMap onGameTimeAdvanced(MapId id, MapOwnerId owner, long expectedVersion, GameTimeAdvanced event) {
        VersionedOwnedCombatMap state=owned(id, owner);
        if(!state.map().adventureId().value().equals(event.adventureId())) throw new IllegalArgumentException("game time event belongs to another adventure");
        String fingerprint=id+"|"+owner+"|TIME|"+event.adventureId()+"|"+event.ruleTurn(); CombatMap replay=replay(id,owner,event.causeId(),fingerprint); if(replay!=null)return replay;
        if(state.version()!=expectedVersion) throw new IllegalStateException("version mismatch");
        if(state.map().visibilitySnapshot()!=null && event.ruleTurn()<state.map().visibilitySnapshot().ruleTurn()) throw new IllegalArgumentException("game time must be monotonic");
        state.map().refreshVisibility(event.ruleTurn()); store.update(owner,state.map(),expectedVersion,expectedVersion+1,event.causeId(),fingerprint); return state.map();
    }
    private PlayerCombatMapView projection(CombatMap map, long version) {
        VisibilitySnapshot visibility = map.visibilitySnapshot();
        if (visibility == null) visibility = new VisibilityPolicy().calculate(map.grid(), playerOrigins(map), Set.of(), map.obstacles(), map.doors(), map.tokens(), Set.of(), 0);
        Set<TokenId> visible = visibility.observedTokens(); Set<TokenId> lastSeenIds=new HashSet<>(); List<CombatToken> exposed = new ArrayList<>(map.tokens().stream().filter(token -> visible.contains(token.id())).toList());
        for (LastSeenState last : visibility.lastSeen()) if (!visible.contains(last.tokenId())) { exposed.add(new CombatToken(last.tokenId(), last.type(), last.position(), TokenController.AI_GAME_MASTER, null)); lastSeenIds.add(last.tokenId()); }
        Set<GridPosition> explored = visibility.explored();
        return new PlayerCombatMapView(map.id(), map.grid(), exposed, map.obstacles().stream().filter(explored::contains).collect(Collectors.toSet()), map.doors().stream().filter(door->explored.contains(door.position())).toList(), map.layers().stream().filter(l -> l.visibility() == LayerVisibility.PLAYER_VISIBLE).toList(), visibility.current(), explored, lastSeenIds, version);
    }
    private CombatMap replay(MapId id,MapOwnerId owner,UUID commandId,String fingerprint){VersionedOwnedCombatMap replay=store.findByCommandId(commandId).orElse(null);if(replay==null)return null;if(!replay.map().id().equals(id)||!replay.owner().equals(owner)||!fingerprint.equals(replay.map().operationFingerprint()))throw new IllegalStateException("command id reused with different payload or owner");return replay.map();}
    private static Set<GridPosition> playerOrigins(CombatMap map) { return map.tokens().stream().filter(t -> t.type() == TokenType.PLAYER).map(CombatToken::position).collect(Collectors.toSet()); }
    private VersionedOwnedCombatMap owned(MapId id, MapOwnerId owner) { VersionedOwnedCombatMap state = store.find(id).orElseThrow(CombatMapAccessDeniedException::new); if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException(); return state; }
    private static CombatToken copy(CombatToken t, GridPosition p) { return new CombatToken(t.id(), t.type(), p, t.controller(), t.ownerPlayerId().orElse(null), t.discovery()); }
}
