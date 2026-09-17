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
import java.util.Optional;
import com.dndmaster.combatmap.domain.AdventureId;

/** Combat Map capability that validates and atomically materializes Scenario Preparation output. */
public final class SpatialFeatureApplicationService {
    private static final String WARNING_LAYER = "SPATIAL_PREPARATION_WARNING";
    private static final String FAILURE_LAYER = "SPATIAL_PREPARATION_FAILURE";

    private final CombatMapViewStore store;
    public SpatialFeatureApplicationService(CombatMapViewStore store) {
        this.store = Objects.requireNonNull(store, "combat map store must not be null");
    }

    public Result prepare(MapId mapId, MapOwnerId owner, SpatialFeaturePlacementBatch batch,
            long createdTurn, SpatialPreparationCommand command) {
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(owner, "map owner must not be null");
        Objects.requireNonNull(batch, "validated spatial placement batch must not be null");
        Objects.requireNonNull(command, "spatial preparation command must not be null");

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

        if (createdTurn < 0) throw new IllegalArgumentException("created turn must not be negative");
        List<com.dndmaster.combatmap.domain.SpatialFeature> features = batch.placements().stream()
                .map(placement -> toFeature(current.map(), placement, createdTurn))
                .toList();
        validateEvidenceVersion(batch, features);
        if (!batch.blocked()) current.map().materializeSpatialFeatures(features);
        CombatMap updated = withDiagnostics(current.map(), batch.warnings(), batch.failures());
        if (batch.blocked()) updated.blockSpatialPreparation();
        else updated.completeSpatialPreparation();
        store.update(owner, updated, command.expectedVersion(), command.expectedVersion() + 1,
                command.commandId(), command.fingerprint());
        return new Result(updated.id(), command.expectedVersion() + 1,
                batch.blocked() ? Status.BLOCKED : Status.READY, batch.warnings().size());
    }

    /** Materializes a generated map and its validated preparation in the store's insert transaction. */
    public Result prepareNew(MapOwnerId owner, CombatMap map, SpatialFeaturePlacementBatch batch,
            long createdTurn, SpatialPreparationCommand command) {
        Objects.requireNonNull(owner, "map owner must not be null");
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(batch, "validated spatial placement batch must not be null");
        Objects.requireNonNull(command, "spatial preparation command must not be null");
        VersionedOwnedCombatMap replay = store.findByCommandId(command.commandId()).orElse(null);
        if (replay != null) {
            if (!replay.map().adventureId().equals(map.adventureId()) || !replay.owner().equals(owner)
                    || !command.fingerprint().equals(replay.map().operationFingerprint())) {
                throw new SpatialPreparationCommandConflictException();
            }
            return resultFrom(replay.map());
        }
        if (command.expectedVersion() != 0) throw new SpatialPreparationVersionConflictException();
        if (createdTurn < 0) throw new IllegalArgumentException("created turn must not be negative");
        List<com.dndmaster.combatmap.domain.SpatialFeature> features = batch.blocked() ? List.of()
                : batch.placements().stream().map(placement -> toFeature(map, placement, createdTurn)).toList();
        validateEvidenceVersion(batch, features);
        map.materializeSpatialFeatures(java.util.stream.Stream.concat(map.spatialFeatures().stream(), features.stream()).toList());
        CombatMap prepared = withDiagnostics(map, batch.warnings(), batch.failures());
        if (batch.blocked()) prepared.blockSpatialPreparation();
        else prepared.completeSpatialPreparation();
        prepared.markPersisted(0, command.commandId(), command.fingerprint());
        store.insert(owner, prepared);
        return new Result(prepared.id(), 0,
                batch.blocked() ? Status.BLOCKED : Status.READY, batch.warnings().size());
    }

    public Optional<Result> replay(AdventureId adventureId, MapOwnerId owner, SpatialPreparationCommand command) {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(owner, "map owner must not be null");
        Objects.requireNonNull(command, "spatial preparation command must not be null");
        VersionedOwnedCombatMap replay = store.findByCommandId(command.commandId()).orElse(null);
        if (replay == null) return Optional.empty();
        if (!replay.map().adventureId().equals(adventureId) || !replay.owner().equals(owner)
                || !command.fingerprint().equals(replay.map().operationFingerprint())) {
            throw new SpatialPreparationCommandConflictException();
        }
        return Optional.of(resultFrom(replay.map()));
    }

    public Optional<Result> replayByCommandId(AdventureId adventureId, MapOwnerId owner, java.util.UUID commandId) {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(owner, "map owner must not be null");
        Objects.requireNonNull(commandId, "command id must not be null");
        VersionedOwnedCombatMap replay = store.findByCommandId(commandId).orElse(null);
        if (replay == null) return Optional.empty();
        if (!replay.map().adventureId().equals(adventureId) || !replay.owner().equals(owner)) {
            throw new SpatialPreparationCommandConflictException();
        }
        return Optional.of(resultFrom(replay.map()));
    }

    private static com.dndmaster.combatmap.domain.SpatialFeature toFeature(CombatMap map,
            SpatialFeaturePlacementBatch.Placement placement, long createdTurn) {
        if (placement.cells().isEmpty()) throw new IllegalArgumentException("spatial feature cells must not be empty");
        if (placement.cells().stream().anyMatch(cell -> !placement.evidence().allowedCells().contains(cell))) {
            throw new IllegalArgumentException("spatial feature cell is outside structured evidence");
        }
        if (placement.cells().stream().anyMatch(cell -> !map.grid().contains(cell) || map.obstacles().contains(cell))) {
            throw new IllegalArgumentException("spatial feature cell is outside playable map facts");
        }
        String source = placement.evidence().sourceDocumentId() + ":"
                + placement.evidence().sourceExtractionVersion() + ":"
                + placement.evidence().sourceLocator() + ":"
                + placement.evidence().resolutionUnitId() + ":"
                + placement.evidence().scenarioPackageVersion();
        var provenance = com.dndmaster.combatmap.domain.SpatialFeatureProvenance.storyPlan(
                source, createdTurn, map.version());
        return com.dndmaster.combatmap.domain.SpatialFeature.prepared(
                placement.featureId(), placement.type(), placement.cells(), placement.detectionSpec(),
                placement.triggers(), provenance, placement.durationTurns(), placement.removalPolicy(),
                placement.overlapAllowed(), placement.repeatable());
    }

    private static void validateEvidenceVersion(SpatialFeaturePlacementBatch batch,
            List<com.dndmaster.combatmap.domain.SpatialFeature> ignored) {
        if (batch.placements().stream().anyMatch(placement ->
                !batch.scenarioPackageVersion().equals(placement.evidence().scenarioPackageVersion()))) {
            throw new IllegalArgumentException("spatial feature evidence does not belong to preparation version");
        }
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
