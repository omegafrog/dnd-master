package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.CombatMapRepository;
import com.dndmaster.combatmap.application.movement.MovementOperationStatus;
import com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperation;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperationRepository;
import com.dndmaster.combatmap.application.movement.MovementStartRequest;
import com.dndmaster.combatmap.application.movement.MovementOperationResponse;
import com.dndmaster.combatmap.application.movement.MovementCommandConflictException;
import com.dndmaster.combatmap.application.movement.CombatMapMovementPreviewMismatchException;
import com.dndmaster.combatmap.application.movement.MovementPreviewRequiredException;
import com.dndmaster.combatmap.application.movement.MovementFinalCommitConflictException;
import com.dndmaster.combatmap.application.movement.MovementResolutionOutcomeStatus;
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
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureProvenance;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialFeatureVisibility;
import com.dndmaster.combatmap.domain.SpatialTrigger;
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
        assertEquals(MovementResolutionOutcomeStatus.COMMITTED, response.result().status());
        assertEquals(1, fixture.map.version());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
        assertTrue(fixture.map.visibilitySnapshot().explored().containsAll(
                Set.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1))));

        MovementOperationResponse replay = fixture.service().start(fixture.start("fingerprint-1"));
        assertEquals(response, replay);
        assertEquals(1, fixture.mapSaves);
    }

    @Test
    void commits_a_normal_interruption_at_the_last_traversed_cell() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service((operation, nextCell) ->
                java.util.Optional.of(new com.dndmaster.combatmap.application.movement.MovementInterruption(
                        "PLAYER_DECISION_REQUIRED", List.of("MOVEMENT_PAUSED"))));

        MovementOperationResponse response = service.start(fixture.start("fingerprint-1"));

        assertEquals(MovementOperationStatus.COMMITTED, response.status());
        assertEquals(MovementResolutionOutcomeStatus.INTERRUPTED, response.outcomeStatus());
        assertEquals(List.of(new GridPosition(1, 1)), response.result().traversedPath());
        assertEquals(new GridPosition(1, 1), response.result().finalPosition());
        assertEquals("PLAYER_DECISION_REQUIRED", response.result().interruptionReason());
        assertEquals(new GridPosition(1, 1), fixture.map.tokens().getFirst().position());
        assertEquals(1, fixture.map.version());
    }

    @Test
    void default_movement_stops_before_a_public_enter_cell_feature_and_persists_interrupted_result() {
        Fixture fixture = new Fixture();
        fixture.map = new CombatMap(fixture.map.id(), fixture.map.adventureId(), fixture.map.ruleSetId(), fixture.map.grid(),
                fixture.player, fixture.map.tokens(), fixture.map.obstacles(), fixture.map.layers(), fixture.map.version(), null, null,
                List.of(new SpatialFeature(UUID.randomUUID(), SpatialFeatureType.HAZARD_AREA,
                        List.of(new GridPosition(2, 1)), SpatialFeatureVisibility.REVEALED, SpatialFeature.State.ACTIVE,
                        null, Set.of(SpatialTrigger.ENTER_CELL), SpatialFeatureProvenance.runtime("public-feature", 1, 0),
                        false, -1)));
        fixture.map.replaceVisibility(new VisibilitySnapshot(
                Set.of(new GridPosition(1, 1), new GridPosition(2, 1)),
                Set.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)), Set.of(), List.of(), 0));

        MovementOperationResponse response = fixture.service().start(fixture.start("fingerprint-1"));

        assertEquals(MovementOperationStatus.COMMITTED, response.status());
        assertEquals(MovementResolutionOutcomeStatus.INTERRUPTED, response.result().status());
        assertEquals(List.of(new GridPosition(1, 1)), response.result().traversedPath());
        assertEquals(new GridPosition(1, 1), response.result().finalPosition());
        assertEquals("SPATIAL_FEATURE_REQUIRES_DECISION", response.result().interruptionReason());
        assertEquals(List.of("SPATIAL_FEATURE_REQUIRES_DECISION"), response.result().publicEvents());
        assertEquals(new GridPosition(1, 1), fixture.map.tokens().getFirst().position());
        assertEquals(1, fixture.map.version());
    }

    @Test
    void staged_start_rechecks_the_public_preview_path_distance_and_fingerprint() {
        Fixture fixture = new Fixture();
        var preview = fixture.service().preview(new com.dndmaster.combatmap.application.movement.MovementPreviewRequest(
                fixture.map.id(), fixture.player, fixture.tokenId, new GridPosition(3, 1), List.of(), "5E", fixture.map.version()));

        assertThrows(CombatMapMovementPreviewMismatchException.class, () -> fixture.service().start(
                fixture.startWithPreview("different-preview", List.of())));
        assertThrows(CombatMapMovementPreviewMismatchException.class, () -> fixture.service().start(
                fixture.startWithPreview(preview.fingerprint(), List.of(new GridPosition(2, 1)))));
        assertEquals(0, fixture.map.version());
    }

    @Test
    void staged_start_requires_a_server_preview_fingerprint() {
        Fixture fixture = new Fixture();

        assertThrows(MovementPreviewRequiredException.class, () -> fixture.service().start(
                new MovementStartRequest(fixture.map.id(), fixture.player, fixture.tokenId, fixture.path,
                        "5E", fixture.commandId, "operation-fingerprint", fixture.map.version())));
        assertEquals(0, fixture.map.version());
    }

    @Test
    void non_terminal_operation_query_returns_the_durable_movement_result_shape() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        operation.advanceTo(1, new GridPosition(2, 1));
        fixture.save(operation);

        MovementOperationResponse response = fixture.service().query(fixture.map.id(), operation.operationId());

        assertEquals(MovementOperationStatus.PREPARING, response.status());
        assertEquals(MovementResolutionOutcomeStatus.CHECK_REQUIRED, response.outcomeStatus());
        assertEquals(fixture.path, response.result().requestedPath());
        assertEquals(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), response.result().traversedPath());
        assertEquals(new GridPosition(2, 1), response.result().finalPosition());
    }

    @Test
    void final_map_version_conflict_is_typed_and_persisted_as_a_durable_conflict_outcome() {
        Fixture fixture = new Fixture();
        fixture.failFinalVersionConflict = true;

        assertThrows(MovementFinalCommitConflictException.class,
                () -> fixture.service().start(fixture.start("fingerprint-1")));

        MovementResolutionOperation operation = fixture.findOperationByCommandId(fixture.commandId).orElseThrow();
        assertEquals(MovementOperationStatus.CANCELLED, operation.status());
        assertEquals(MovementResolutionOutcomeStatus.CANCELLED, operation.result().status());
        assertEquals("MAP_VERSION_CONFLICT", operation.result().interruptionReason());
        assertEquals(0, fixture.map.version());
    }

    @Test
    void rejects_same_command_with_a_different_fingerprint_without_changing_the_map() {
        Fixture fixture = new Fixture();
        MovementStartRequest first = fixture.start("fingerprint-1");
        fixture.service().start(first);

        assertThrows(MovementCommandConflictException.class, () -> fixture.service().start(fixture.start("fingerprint-2")));
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
        assertEquals(1, fixture.map.version());
    }

    @Test
    void concurrent_start_of_the_same_command_replays_the_winning_reservation() {
        Fixture fixture = new Fixture();
        fixture.concurrentReservationFingerprint = "fingerprint-1";

        MovementOperationResponse replay = fixture.service().start(fixture.start("fingerprint-1"));

        assertEquals(fixture.commandId, fixture.findById(replay.operationId()).orElseThrow().commandId());
        assertEquals(MovementOperationStatus.PREPARING, replay.status());
        assertEquals(0, fixture.mapSaves);
    }

    @Test
    void concurrent_start_of_the_same_command_with_another_fingerprint_is_a_typed_conflict() {
        Fixture fixture = new Fixture();
        fixture.concurrentReservationFingerprint = "fingerprint-from-other-request";

        assertThrows(MovementCommandConflictException.class,
                () -> fixture.service().start(fixture.start("fingerprint-1")));
        assertEquals(0, fixture.mapSaves);
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
                () -> fixture.service().start(fixture.startWithCommand(UUID.randomUUID(), "fingerprint-2")));

        fixture.delete(active.operationId());
        fixture.map.markPersisted(1, UUID.randomUUID(), "other");
        var currentPreview = fixture.service().preview(new com.dndmaster.combatmap.application.movement.MovementPreviewRequest(
                fixture.map.id(), fixture.player, fixture.tokenId, fixture.path.orderedPositions().getLast(), List.of(), "5E", 1));
        assertThrows(com.dndmaster.combatmap.application.movement.MovementVersionConflictException.class,
                () -> fixture.service().start(new MovementStartRequest(fixture.map.id(), fixture.player, fixture.tokenId,
                        fixture.path, "5E", fixture.commandId, "fingerprint-2", currentPreview.fingerprint(), List.of(), 0)));
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
    void restart_commits_a_ready_operation_from_its_durable_result_without_repreparing_it() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = fixture.readyOperation();
        fixture.save(operation);

        MovementOperationResponse response = fixture.service().resume(fixture.map.id(), operation.operationId());

        assertEquals(MovementOperationStatus.COMMITTED, response.status());
        assertEquals(operation.traversedPath(), response.result().traversedPath());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
        assertEquals(1, fixture.mapSaves);
    }

    @Test
    void does_not_save_again_after_the_atomic_commit_already_persists_the_operation() {
        Fixture fixture = new Fixture();
        fixture.atomicCommitPersistsOperation = true;
        MovementResolutionOperation operation = fixture.readyOperation();
        fixture.save(operation);

        MovementOperationResponse response = fixture.service().resume(fixture.map.id(), operation.operationId());

        assertEquals(MovementOperationStatus.COMMITTED, response.status());
        assertEquals(0, fixture.operationSavesAfterAtomicCommit);
    }

    @Test
    void retry_wait_preserves_a_prepared_result_and_resumes_the_ready_to_commit_state() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = fixture.readyOperation();
        operation.retryWait(3);
        fixture.save(operation);

        MovementOperationResponse waiting = fixture.service().query(fixture.map.id(), operation.operationId());
        assertEquals(MovementOperationStatus.RETRY_WAIT, waiting.status());
        assertEquals(operation.traversedPath(), waiting.result().traversedPath());

        MovementOperationResponse committed = fixture.service().resume(fixture.map.id(), operation.operationId());
        assertEquals(MovementOperationStatus.COMMITTED, committed.status());
        assertEquals(operation.traversedPath(), committed.result().traversedPath());
    }

    @Test
    void recovery_worker_resumes_all_durable_non_terminal_operations() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        operation.advanceTo(1, new GridPosition(2, 1));
        fixture.save(operation);

        List<MovementOperationResponse> recovered = fixture.service().recoverIncompleteOperations();

        assertEquals(1, recovered.size());
        assertEquals(MovementOperationStatus.COMMITTED, recovered.getFirst().status());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
    }

    @Test
    void recovery_worker_defers_one_broken_operation_without_skipping_other_durable_work() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation broken = MovementResolutionOperation.start(UUID.randomUUID(), new MapId(UUID.randomUUID()),
                UUID.randomUUID(), fixture.player, fixture.tokenId, fixture.path, "broken-fingerprint", 0);
        MovementResolutionOperation recoverable = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        recoverable.advanceTo(1, new GridPosition(2, 1));
        fixture.operations.put(broken.operationId(), broken);
        fixture.operations.put(recoverable.operationId(), recoverable);

        List<MovementOperationResponse> recovered = fixture.service().recoverIncompleteOperations();

        assertEquals(2, recovered.size());
        assertEquals(MovementOperationStatus.PREPARING,
                fixture.findById(broken.operationId()).orElseThrow().status());
        assertEquals(MovementOperationStatus.COMMITTED,
                fixture.findById(recoverable.operationId()).orElseThrow().status());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
    }

    @Test
    void runtime_recovery_poll_retries_waiting_work_without_racing_a_preparing_request() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation preparing = MovementResolutionOperation.start(UUID.randomUUID(), new MapId(UUID.randomUUID()),
                UUID.randomUUID(), fixture.player, fixture.tokenId, fixture.path, "preparing-fingerprint", 0);
        MovementResolutionOperation waiting = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        waiting.advanceTo(1, new GridPosition(2, 1));
        waiting.retryWait(3);
        fixture.operations.put(preparing.operationId(), preparing);
        fixture.operations.put(waiting.operationId(), waiting);

        List<MovementOperationResponse> recovered = fixture.service().retryWaitingOperations();

        assertEquals(1, recovered.size());
        assertEquals(MovementOperationStatus.PREPARING,
                fixture.findById(preparing.operationId()).orElseThrow().status());
        assertEquals(MovementOperationStatus.COMMITTED,
                fixture.findById(waiting.operationId()).orElseThrow().status());
    }

    @Test
    void startup_recovery_defers_a_transient_operation_list_failure() {
        Fixture fixture = new Fixture();
        MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), fixture.map.id(),
                fixture.commandId, fixture.player, fixture.tokenId, fixture.path, "fingerprint-1", 0);
        fixture.save(operation);
        fixture.failRecoveryLoad = true;

        assertEquals(List.of(), fixture.service().recoverIncompleteOperations());
        assertEquals(new GridPosition(1, 1), fixture.map.tokens().getFirst().position());
        assertEquals(0, fixture.map.version());

        fixture.failRecoveryLoad = false;
        List<MovementOperationResponse> recovered = fixture.service().recoverStalledOperations(java.time.Instant.now());

        assertEquals(1, recovered.size());
        assertEquals(MovementOperationStatus.COMMITTED, recovered.getFirst().status());
        assertEquals(new GridPosition(3, 1), fixture.map.tokens().getFirst().position());
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
                0, new GridPosition(1, 1), List.of(new GridPosition(1, 1)), null, 0, operation.persistenceVersion(),
                MovementOperationStatus.PREPARING);
        operation.advanceTo(1, new GridPosition(2, 1));
        fixture.save(operation);

        assertThrows(com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException.class,
                () -> fixture.save(stale));
    }

    @Test
    void concurrent_operation_update_is_not_converted_into_a_cancellation() {
        Fixture fixture = new Fixture();
        fixture.failNextOperationProgressSave = true;

        MovementOperationConcurrentUpdateException failure = org.junit.jupiter.api.Assertions.assertThrows(
                MovementOperationConcurrentUpdateException.class,
                () -> fixture.service().start(fixture.start("fingerprint-1")));

        assertEquals("movement operation changed concurrently", failure.getMessage());
        assertEquals(MovementOperationStatus.PREPARING,
                fixture.findOperationByCommandId(fixture.commandId).orElseThrow().status());
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
        boolean failFinalVersionConflict;
        boolean failRecoveryLoad;
        boolean atomicCommitPersistsOperation;
        int operationSavesAfterAtomicCommit;
        boolean failNextOperationProgressSave;
        String concurrentReservationFingerprint;
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

        CombatMapMovementService service(com.dndmaster.combatmap.application.movement.MovementInterruptionPolicy policy) {
            return new CombatMapMovementService(this, (ruleSet, edition) -> 30, this, policy);
        }

        MovementStartRequest start(String fingerprint) {
            var preview = service().preview(new com.dndmaster.combatmap.application.movement.MovementPreviewRequest(
                    map.id(), player, tokenId, path.orderedPositions().getLast(), List.of(), "5E", map.version()));
            return new MovementStartRequest(map.id(), player, tokenId, path, "5E", commandId, fingerprint,
                    preview.fingerprint(), List.of(), map.version());
        }

        MovementStartRequest startWithPreview(String previewFingerprint, List<GridPosition> waypoints) {
            return new MovementStartRequest(map.id(), player, tokenId, path, "5E", commandId, "operation-fingerprint",
                    previewFingerprint, waypoints, map.version());
        }

        MovementStartRequest startWithCommand(UUID commandId, String fingerprint) {
            return startWithCommand(commandId, fingerprint, map.version());
        }

        MovementStartRequest startWithCommand(UUID commandId, String fingerprint, long expectedVersion) {
            var preview = service().preview(new com.dndmaster.combatmap.application.movement.MovementPreviewRequest(
                    map.id(), player, tokenId, path.orderedPositions().getLast(), List.of(), "5E", expectedVersion));
            return new MovementStartRequest(map.id(), player, tokenId, path, "5E", commandId, fingerprint,
                    preview.fingerprint(), List.of(), expectedVersion);
        }

        MovementResolutionOperation readyOperation() {
            MovementResolutionOperation operation = MovementResolutionOperation.start(UUID.randomUUID(), map.id(), commandId,
                    player, tokenId, path, "fingerprint-1", 0);
            operation.advanceTo(1, new GridPosition(2, 1));
            operation.advanceTo(2, new GridPosition(3, 1));
            operation.readyToCommit(new com.dndmaster.combatmap.application.movement.MovementResolutionResult(
                    operation.requestedPath(), operation.traversedPath(), operation.currentCell(), 1, List.of(), null));
            return operation;
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
        @Override public MovementResolutionOperation reserve(MovementResolutionOperation operation) {
            if (concurrentReservationFingerprint != null) {
                MovementResolutionOperation winner = MovementResolutionOperation.start(UUID.randomUUID(), map.id(), commandId,
                        player, tokenId, path, concurrentReservationFingerprint, 0);
                operations.put(winner.operationId(), winner);
                concurrentReservationFingerprint = null;
                if (!winner.fingerprint().equals(operation.fingerprint())) throw new MovementCommandConflictException();
                return winner;
            }
            operations.put(operation.operationId(), operation);
            return operation;
        }
        @Override public void save(MovementResolutionOperation operation) {
            if (failNextOperationProgressSave && operation.status() == MovementOperationStatus.PREPARING
                    && operation.cursor() == 1) {
                failNextOperationProgressSave = false;
                throw new MovementOperationConcurrentUpdateException();
            }
            if (atomicCommitPersistsOperation && operation.status() == MovementOperationStatus.COMMITTED) {
                operationSavesAfterAtomicCommit++;
            }
            MovementResolutionOperation stored = operations.get(operation.operationId());
            if (stored != null && stored != operation && stored.persistenceVersion() != operation.persistenceVersion()) {
                throw new com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException();
            }
            operation.markPersisted(operation.persistenceVersion() + (stored == null ? 0 : 1));
            operations.put(operation.operationId(), operation);
        }

        @Override
        public void commitMovementResolution(CombatMap map, long persistedVersion,
                MovementResolutionOperation operation,
                com.dndmaster.combatmap.application.movement.MovementResolutionResult result) {
            if (failFinalVersionConflict) throw new MovementFinalCommitConflictException();
            if (!atomicCommitPersistsOperation) {
                CombatMapRepository.super.commitMovementResolution(map, persistedVersion, operation, result);
                return;
            }
            save(map, persistedVersion, operation.commandId(), operation.fingerprint());
            operation.committed(result);
            operation.markPersisted(operation.persistenceVersion() + 1);
        }
        void delete(UUID id) { operations.remove(id); }
        @Override public List<MovementResolutionOperation> findRecoverable() {
            if (failRecoveryLoad) throw new com.dndmaster.combatmap.infrastructure.persistence.CombatMapPersistenceException(
                    "recovery load failed", null);
            return operations.values().stream().filter(operation -> operation.status().active()).toList();
        }

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
