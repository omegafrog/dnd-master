package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.application.view.CombatMapAccessDeniedException;
import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.LineOfSightQuery;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adventure가 호출하는 #330 공간 행동 경계. 공개 시야와 플레이어 소유권을 여기서 검사한다. */
public final class SpatialFeatureRuntimeApplicationService {
    private final CombatMapViewStore store;
    private final SpatialFeatureRuntimeService runtime;
    private final LineOfSightQuery lineOfSight;

    public SpatialFeatureRuntimeApplicationService(CombatMapViewStore store) {
        this(store, new SpatialFeatureRuntimeService(), new LineOfSightQuery());
    }

    SpatialFeatureRuntimeApplicationService(CombatMapViewStore store, SpatialFeatureRuntimeService runtime,
            LineOfSightQuery lineOfSight) {
        this.store = Objects.requireNonNull(store, "combat map store must not be null");
        this.runtime = Objects.requireNonNull(runtime, "spatial runtime must not be null");
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "line-of-sight query must not be null");
    }

    public SpatialRuntimeResult observe(MapId mapId, MapOwnerId owner, TokenId tokenId, GridPosition cell,
            long expectedVersion, UUID commandId) {
        return act(mapId, owner, tokenId, cell, expectedVersion, commandId, "OBSERVE", runtime::observe);
    }

    public SpatialRuntimeResult interact(MapId mapId, MapOwnerId owner, TokenId tokenId, GridPosition cell,
            long expectedVersion, UUID commandId) {
        return act(mapId, owner, tokenId, cell, expectedVersion, commandId, "INTERACT", runtime::interact);
    }

    public SpatialRuntimeResult combatTurnStart(MapId mapId, MapOwnerId owner, long expectedVersion, UUID commandId) {
        VersionedOwnedCombatMap state = owned(mapId, owner);
        String fingerprint = fingerprint(mapId, owner, "COMBAT_TURN_START", commandId);
        CombatMap replay = replay(state, owner, commandId, fingerprint);
        if (replay != null) return result(replay, List.of());
        requireVersion(state, expectedVersion);
        List<String> events = runtime.combatTurnStart(state.map());
        return save(state, owner, commandId, fingerprint, events);
    }

    public SpatialRuntimeResult advanceDurations(MapId mapId, MapOwnerId owner, long expectedVersion, UUID commandId) {
        VersionedOwnedCombatMap state = owned(mapId, owner);
        String fingerprint = fingerprint(mapId, owner, "ADVANCE_DURATIONS", commandId);
        CombatMap replay = replay(state, owner, commandId, fingerprint);
        if (replay != null) return result(replay, List.of());
        requireVersion(state, expectedVersion);
        runtime.advanceDurations(state.map());
        return save(state, owner, commandId, fingerprint, List.of());
    }

    private SpatialRuntimeResult act(MapId mapId, MapOwnerId owner, TokenId tokenId, GridPosition cell,
            long expectedVersion, UUID commandId, String action, CellAction operation) {
        VersionedOwnedCombatMap state = owned(mapId, owner);
        String fingerprint = fingerprint(mapId, owner, action, tokenId, cell);
        CombatMap replay = replay(state, owner, commandId, fingerprint);
        if (replay != null) return result(replay, List.of());
        requireVersion(state, expectedVersion);
        GridPosition origin = state.map().playerTokenPosition(new PlayerId(owner.value()), tokenId);
        requireVisibleCell(state.map(), origin, cell);
        List<String> events = operation.apply(state.map(), cell);
        return save(state, owner, commandId, fingerprint, events);
    }

    private SpatialRuntimeResult save(VersionedOwnedCombatMap state, MapOwnerId owner, UUID commandId,
            String fingerprint, List<String> events) {
        long nextVersion = state.version() + 1;
        store.update(owner, state.map(), state.version(), nextVersion, commandId, fingerprint);
        return new SpatialRuntimeResult(state.map().id(), nextVersion, events);
    }

    private void requireVisibleCell(CombatMap map, GridPosition origin, GridPosition cell) {
        if (cell == null || map.visibilitySnapshot() == null || !map.visibilitySnapshot().current().contains(cell)) {
            throw new IllegalArgumentException("spatial action cell is outside current visible scope");
        }
        HashSet<GridPosition> blockers = new HashSet<>(map.obstacles());
        map.doors().stream().filter(door -> !door.open()).map(Door::position).forEach(blockers::add);
        if (!lineOfSight.clear(origin, cell, blockers, map.publicBoundaries())) {
            throw new IllegalArgumentException("spatial action cell is blocked from player sight");
        }
    }

    private VersionedOwnedCombatMap owned(MapId mapId, MapOwnerId owner) {
        VersionedOwnedCombatMap state = store.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        return state;
    }

    private static void requireVersion(VersionedOwnedCombatMap state, long expectedVersion) {
        if (state.version() != expectedVersion) throw new IllegalStateException("version mismatch");
    }

    private CombatMap replay(VersionedOwnedCombatMap state, MapOwnerId owner, UUID commandId, String fingerprint) {
        if (commandId == null) throw new IllegalArgumentException("command id is required");
        VersionedOwnedCombatMap history = store.findByCommandId(commandId).orElse(null);
        if (history == null) return null;
        if (!history.owner().equals(owner) || !history.map().id().equals(state.map().id())
                || !fingerprint.equals(history.map().operationFingerprint())) {
            throw new IllegalArgumentException("command id reused with different spatial action");
        }
        return history.map();
    }

    private static SpatialRuntimeResult result(CombatMap map, List<String> events) {
        return new SpatialRuntimeResult(map.id(), map.version(), events);
    }

    private static String fingerprint(Object... values) {
        return java.util.Arrays.stream(values).map(String::valueOf).reduce((left, right) -> left + "|" + right).orElse("");
    }

    @FunctionalInterface
    private interface CellAction {
        List<String> apply(CombatMap map, GridPosition cell);
    }
}
