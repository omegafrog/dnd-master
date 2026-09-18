package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.CombatMapRepository;
import com.dndmaster.combatmap.application.movement.MovementOperationStatus;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperation;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperationRepository;
import com.dndmaster.combatmap.application.movement.MovementStartRequest;
import com.dndmaster.combatmap.application.movement.MovementOperationResponse;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import com.dndmaster.combatmap.domain.VisibilitySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovementResolutionOperationTest {
    @Test
    void resolves_cells_in_order_and_commits_position_visibility_and_version_once() {
        Fixture fixture = new Fixture();
        MovementOperationResponse response = fixture.service().start(fixture.start("fingerprint-1"));

        assertEquals(MovementOperationStatus.COMMITTED, response.status());
        assertEquals(List.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)),
                response.result().traversedPath());
        assertEquals(new GridPosition(3, 1), response.result().finalPosition());
        assertEquals(1, fixture.map.version());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
        assertTrue(fixture.map.visibilitySnapshot().explored().containsAll(
                Set.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1))));

        MovementOperationResponse replay = fixture.service().start(fixture.start("fingerprint-1"));
        assertEquals(response, replay);
        assertEquals(1, fixture.mapSaves);
    }

    @Test
    void rejects_same_command_with_a_different_fingerprint_without_changing_the_map() {
        Fixture fixture = new Fixture();
        MovementStartRequest first = fixture.start("fingerprint-1");
        fixture.service().start(first);

        assertThrows(IllegalStateException.class, () -> fixture.service().start(fixture.start("fingerprint-2")));
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
        assertEquals(1, fixture.map.version());
    }

    @Test
    void replays_an_active_reservation_before_applying_the_map_reservation_conflict_rule() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        operation.retryWait(3);
        fixture.save(operation);

        MovementOperationResponse replay = fixture.service().start(fixture.start("fingerprint-1"));

        assertEquals(operation.operationId(), replay.operationId());
        assertEquals(MovementOperationStatus.RETRY_WAIT, replay.status());
        assertEquals(0, fixture.mapSaves);
    }

    @Test
    void distinguishes_an_active_reservation_from_an_expected_version_conflict() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation active = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        active.readyToCommit();
        fixture.save(active);

        assertThrows(com.dndmaster.combatmap.application.movement.MovementReservationConflictException.class,
                () -> fixture.service().start(new MovementStartRequest(fixture.map.id(), fixture.player, fixture.tokenId,
                        fixture.path, "5E", UUID.randomUUID(), "fingerprint-2", 0)));

        fixture.delete(active.operationId());
        fixture.map.markPersisted(1, UUID.randomUUID(), "other");
        assertThrows(com.dndmaster.combatmap.application.movement.MovementVersionConflictException.class,
                () -> fixture.service().start(new MovementStartRequest(fixture.map.id(), fixture.player, fixture.tokenId,
                        fixture.path, "5E", fixture.commandId, "fingerprint-2", 0)));
    }

    @Test
    void final_save_failure_cancels_the_operation_and_leaves_the_public_map_unchanged() {
        Fixture fixture = new Fixture();
        fixture.failFinalSave = true;

        MovementOperationResponse response = fixture.service().start(fixture.start("fingerprint-1"));

        assertEquals(new GridPosition(1, 1), fixture.map.tokens().getFirst().position());
        assertEquals(0, fixture.map.version());
        assertEquals(MovementOperationStatus.RETRY_WAIT, response.status());
        assertEquals(MovementOperationStatus.RETRY_WAIT,
                fixture.findOperationByCommandId(fixture.commandId).orElseThrow().status());
    }

    @Test
    void restart_rebuilds_staged_position_and_visibility_before_continuing_from_saved_cursor() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        operation.advanceTo(1, new GridPosition(2, 1));
        operation.retryWait(3);
        fixture.save(operation);

        MovementOperationResponse response = fixture.service().resume(fixture.map.id(), operation.operationId());

        assertEquals(MovementOperationStatus.COMMITTED, response.status());
        assertEquals(List.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)),
                response.result().traversedPath());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
        assertTrue(fixture.map.visibilitySnapshot().explored().contains(new GridPosition(2, 1)));
        assertEquals(1, fixture.mapSaves);
    }

    @Test
    void operation_access_requires_the_path_map_to_match_the_reserved_map() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        fixture.save(operation);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service().query(new MapId(UUID.randomUUID()), operation.operationId()));
        assertEquals(MovementOperationStatus.PREPARING, fixture.service().query(fixture.map.id(), operation.operationId()).status());
    }

    @Test
    void operation_state_transitions_keep_cursor_and_current_cell_for_restart() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);

        operation.advanceTo(1, new GridPosition(2, 1));
        assertEquals(MovementOperationStatus.PREPARING, operation.status());
        assertEquals(1, operation.cursor());
        assertEquals(new GridPosition(2, 1), operation.currentCell());
        assertEquals(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), operation.traversedPath());

        operation.readyToCommit();
        operation.committed(new com.dndmaster.combatmap.application.movement.MovementResolutionResult(
                operation.requestedPath(), operation.traversedPath(), operation.currentCell(), 1, List.of(), null));
        assertEquals(MovementOperationStatus.COMMITTED, operation.status());
    }

    @Test
    void retry_exhaustion_cancels_without_publishing_the_staged_map() {
        Fixture fixture = new Fixture();
        fixture.failFinalSave = true;

        MovementOperationResponse response = fixture.service().start(fixture.start("fingerprint-1"));
        for (int attempt = 0; attempt < 3; attempt++) response = fixture.service().resume(fixture.map.id(), response.operationId());

        assertEquals(MovementOperationStatus.CANCELLED, response.status());
        assertEquals(new GridPosition(1, 1), fixture.map.tokens().getFirst().position());
        assertEquals(0, fixture.map.version());
        assertEquals("RETRY_EXHAUSTED", response.result().interruptionReason());
    }

    @Test
    void stale_operation_save_is_rejected_instead_of_overwriting_newer_state() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        fixture.reserve(operation);
        MovementResolutionOperation stale = MovementResolutionOperation.restore(operation.operationId(), fixture.map.id(), fixture.commandId,
                fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0, MovementOperationStatus.PREPARING,
                0, new GridPosition(1, 1), List.of(new GridPosition(1, 1)), null, 0, operation.persistenceVersion());
        operation.advanceTo(1, new GridPosition(2, 1));
        fixture.save(operation);

        assertThrows(com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException.class,
                () -> fixture.save(stale));
    }

    private static final class Fixture implements CombatMapRepository, MovementResolutionOperationRepository {
        final PlayerId player = new PlayerId(UUID.randomUUID());
        final TokenId tokenId = new TokenId(UUID.randomUUID());
        final TokenId unused = new TokenId(UUID.randomUUID());
        final UUID commandId = UUID.randomUUID();
        final MovementPath path = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)), 10);
        CombatMap map;
        int mapSaves;
        boolean failFinalSave;
        final Map<UUID, MovementResolutionOperation> operations = new HashMap<>();

        Fixture() {
            CombatToken token = new CombatToken(tokenId, TokenType.PLAYER, new GridPosition(1, 1), TokenController.PLAYER, player);
            map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                    new GridSpec(6, 4, 50, 5), player,
                    List.of(token), List.of(), List.of(new MapLayer("LIGHT", "bright", LayerVisibility.PLAYER_VISIBLE)), 0, null);
            Set<GridPosition> known = Set.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1));
            map.replaceVisibility(new VisibilitySnapshot(Set.of(new GridPosition(1, 1)), known, Set.of(), List.of(), 0));
        }

        CombatMapMovementService service() {
            return new CombatMapMovementService(this, (ruleSet, edition) -> 30, this);
        }

        MovementStartRequest start(String fingerprint) {
            return new MovementStartRequest(map.id(), player, tokenId, path, "5E", commandId, fingerprint, map.version());
        }

        @Override public Optional<CombatMap> findById(MapId id) { return id.equals(map.id()) ? Optional.of(copy(map)) : Optional.empty(); }
        @Override public Optional<CombatMap> findByCommandId(UUID id) { return Optional.empty(); }
        @Override public void save(CombatMap map) { mapSaves++; this.map = copy(map); }
        @Override public void save(CombatMap map, long persistedVersion, UUID operationKey, String operationFingerprint) {
            if (failFinalSave) throw new com.dndmaster.combatmap.infrastructure.persistence.CombatMapPersistenceException("final save failed", null);
            map.markPersisted(persistedVersion, operationKey, operationFingerprint);
            mapSaves++;
            this.map = copy(map);
        }
        @Override public Optional<MovementResolutionOperation> findById(UUID id) { return Optional.ofNullable(operations.get(id)); }
        @Override public Optional<MovementResolutionOperation> findOperationByCommandId(UUID id) {
            return operations.values().stream().filter(operation -> operation.commandId().equals(id)).findFirst();
        }
        @Override public Optional<MovementResolutionOperation> findActiveByMapId(MapId id) {
            return operations.values().stream().filter(operation -> operation.mapId().equals(id) && operation.status().active()).findFirst();
        }
        @Override public void reserve(MovementResolutionOperation operation) { operations.put(operation.operationId(), operation); }
        @Override public void save(MovementResolutionOperation operation) {
            MovementResolutionOperation stored = operations.get(operation.operationId());
            if (stored != null && stored != operation && stored.persistenceVersion() != operation.persistenceVersion()) {
                throw new com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException();
            }
            operation.markPersisted(operation.persistenceVersion() + (stored == null ? 0 : 1));
            operations.put(operation.operationId(), operation);
        }
        void delete(UUID id) { operations.remove(id); }

        private static CombatMap copy(CombatMap source) {
            List<CombatToken> tokens = source.tokens().stream().map(token -> new CombatToken(token.id(), token.type(), token.position(),
                    token.controller(), token.ownerPlayerId().orElse(null), token.discovery())).toList();
            CombatMap copy = new CombatMap(source.id(), source.adventureId(), source.ruleSetId(), source.grid(), source.ownerPlayerId(), tokens,
                    source.obstacles(), source.layers(), source.version(), source.operationKey(), source.operationFingerprint(), source.spatialFeatures(),
                    source.spatialPreparationBlocked());
            if (source.visibilitySnapshot() != null) copy.replaceVisibility(source.visibilitySnapshot());
            copy.replaceDoors(source.doors());
            copy.replaceRuntimeState(source.runtimeState());
            return copy;
        }
    }
}
