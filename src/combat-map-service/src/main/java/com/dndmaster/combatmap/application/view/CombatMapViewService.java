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
        map.replaceVisibility(new VisibilityPolicy().calculate(map.grid(), playerOrigins(map), Set.of(), map.obstacles(), map.tokens(), Set.of(), 0));
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
        updated.replaceVisibility(new VisibilityPolicy().calculate(updated.grid(), playerOrigins(updated), prior == null ? Set.of() : prior.explored(), updated.obstacles(), updated.tokens(), prior == null ? Set.of() : prior.lastSeen(), prior == null ? 0 : prior.ruleTurn() + 1));
        store.update(owner, updated, expectedVersion); return updated;
    }
    public PlayerCombatMapView displayForPlayer(MapId id, MapOwnerId owner) { VersionedOwnedCombatMap state = owned(id, owner); return projection(state.map(), state.version()); }
    public Optional<PlayerCombatMapView> displayForAdventure(AdventureId adventureId, MapOwnerId owner) { return store.findByAdventureId(adventureId).filter(state -> state.owner().equals(owner)).map(state -> projection(state.map(), state.version())); }
    private PlayerCombatMapView projection(CombatMap map, long version) {
        VisibilitySnapshot visibility = map.visibilitySnapshot();
        if (visibility == null) visibility = new VisibilityPolicy().calculate(map.grid(), playerOrigins(map), Set.of(), map.obstacles(), map.tokens(), Set.of(), 0);
        Set<TokenId> visible = visibility.observedTokens(); List<CombatToken> exposed = new ArrayList<>(map.tokens().stream().filter(token -> visible.contains(token.id())).toList());
        for (LastSeenState last : visibility.lastSeen()) if (!visible.contains(last.tokenId())) exposed.add(new CombatToken(last.tokenId(), last.type(), last.position(), TokenController.AI_GAME_MASTER, null));
        return new PlayerCombatMapView(map.id(), map.grid(), exposed, map.obstacles(), map.layers().stream().filter(l -> l.visibility() == LayerVisibility.PLAYER_VISIBLE).toList(), visibility.current(), visibility.explored(), version);
    }
    private static Set<GridPosition> playerOrigins(CombatMap map) { return map.tokens().stream().filter(t -> t.type() == TokenType.PLAYER).map(CombatToken::position).collect(Collectors.toSet()); }
    private VersionedOwnedCombatMap owned(MapId id, MapOwnerId owner) { VersionedOwnedCombatMap state = store.find(id).orElseThrow(CombatMapAccessDeniedException::new); if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException(); return state; }
    private static CombatToken copy(CombatToken t, GridPosition p) { return new CombatToken(t.id(), t.type(), p, t.controller(), t.ownerPlayerId().orElse(null)); }
}
