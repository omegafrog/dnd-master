package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Combat Map capability that validates and atomically materializes Scenario Preparation output. */
public final class SpatialFeatureApplicationService {
    private static final String WARNING_LAYER = "SPATIAL_PREPARATION_WARNING";
    private static final String FAILURE_LAYER = "SPATIAL_PREPARATION_FAILURE";

    private final CombatMapViewStore store;
    private final SpatialFeaturePreparationService preparation;

    public SpatialFeatureApplicationService(CombatMapViewStore store, SpatialFeaturePreparationService preparation) {
        this.store = Objects.requireNonNull(store, "combat map store must not be null");
        this.preparation = Objects.requireNonNull(preparation, "spatial preparation must not be null");
    }

    public Result prepare(MapId mapId, MapOwnerId owner, SpatialFeaturePreparationInput input,
            long createdTurn, SpatialPreparationCommand command) {
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(owner, "map owner must not be null");
        Objects.requireNonNull(input, "spatial preparation input must not be null");
        Objects.requireNonNull(command, "spatial preparation command must not be null");

        if (input.requirements().isEmpty()) {
            VersionedOwnedCombatMap current = store.find(mapId)
                    .orElseThrow(() -> new IllegalArgumentException("combat map not found"));
            if (!current.owner().equals(owner)) throw new IllegalArgumentException("combat map owner mismatch");
            return new Result(current.map().id(), current.version(),
                    current.map().spatialPreparationBlocked() ? Status.BLOCKED : Status.READY, 0);
        }

        VersionedOwnedCombatMap replay = store.findByCommandId(command.commandId()).orElse(null);
        if (replay != null) {
            if (!replay.map().id().equals(mapId) || !replay.owner().equals(owner)) {
                throw new SpatialPreparationCommandConflictException();
            }
            if (!command.fingerprint().equals(replay.map().operationFingerprint())) {
                throw new SpatialPreparationCommandConflictException();
            }
            return resultFrom(replay.map());
        }

        VersionedOwnedCombatMap current = store.find(mapId)
                .orElseThrow(() -> new IllegalArgumentException("combat map not found"));
        if (!current.owner().equals(owner)) throw new IllegalArgumentException("combat map owner mismatch");
        if (current.version() != command.expectedVersion()) throw new SpatialPreparationVersionConflictException();

        SpatialFeaturePreparationService.Result prepared = preparation.prepare(current.map(), input, createdTurn);
        CombatMap updated = withDiagnostics(current.map(), prepared.warnings(), prepared.failures());
        store.update(owner, updated, command.expectedVersion(), command.expectedVersion() + 1,
                command.commandId(), command.fingerprint());
        return new Result(updated.id(), command.expectedVersion() + 1,
                prepared.activationAllowed() ? Status.READY : Status.BLOCKED,
                prepared.warnings().size());
    }

    private static CombatMap withDiagnostics(CombatMap map, List<String> warnings, List<String> failures) {
        List<MapLayer> layers = new ArrayList<>(map.layers().stream()
                .filter(layer -> !layer.type().equals(WARNING_LAYER) && !layer.type().equals(FAILURE_LAYER)).toList());
        warnings.forEach(warning -> layers.add(new MapLayer(WARNING_LAYER, warning, LayerVisibility.AI_ONLY)));
        failures.forEach(failure -> layers.add(new MapLayer(FAILURE_LAYER, failure, LayerVisibility.AI_ONLY)));
        CombatMap updated = new CombatMap(map.id(), map.adventureId(), map.ruleSetId(), map.grid(), map.ownerPlayerId(),
                map.tokens(), map.obstacles(), layers, map.version(), map.operationKey(), map.operationFingerprint(),
                map.spatialFeatures(), map.spatialPreparationBlocked());
        updated.replaceDoors(map.doors());
        updated.replaceRuntimeState(map.runtimeState());
        if (map.visibilitySnapshot() != null) updated.replaceVisibility(map.visibilitySnapshot());
        return updated;
    }

    private static Result resultFrom(CombatMap map) {
        long warnings = map.layers().stream().filter(layer -> layer.type().equals(WARNING_LAYER)).count();
        return new Result(map.id(), map.version(), map.spatialPreparationBlocked() ? Status.BLOCKED : Status.READY,
                Math.toIntExact(warnings));
    }

    public enum Status { READY, BLOCKED }

    public record Result(MapId mapId, long version, Status status, int warningCount) {
        public Result {
            Objects.requireNonNull(mapId, "result map id must not be null");
            Objects.requireNonNull(status, "result status must not be null");
            if (version < 0 || warningCount < 0) throw new IllegalArgumentException("invalid preparation result");
        }

        public boolean activationAllowed() { return status == Status.READY; }
    }
}
